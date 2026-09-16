package com.cinebase.movieservice.artwork

import com.cinebase.movieservice.config.UploadStorage
import com.cinebase.movieservice.movie.Movie
import com.cinebase.movieservice.movie.MovieService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.util.Optional

/**
 * Upload rules of [ArtworkService] in isolation: the type/size/emptiness gate, the file cleanup it
 * hands to storage, and the cover-artwork bookkeeping after an upload or a removal.
 *
 * The happy path is also exercised through GraphQL by `MovieServiceGraphQlIntegrationTest`, but the
 * rejection cases can only be produced cheaply here.
 */
class ArtworkServiceTest {

    // relaxedUnitFun so Unit-returning repository methods (delete) need no stubbing.
    private val artworks = mockk<ArtworkRepository>(relaxUnitFun = true)
    private val movies = mockk<MovieService>(relaxUnitFun = true)
    private val storage = mockk<UploadStorage>(relaxUnitFun = true)

    private val service = ArtworkService(artworks, movies, storage)

    @BeforeEach
    fun stubSaving() {
        every { artworks.save(any()) } returnsArgument 0
        every { movies.get(1L) } returns Movie(id = 1, title = "Interstellar")
        // Nothing is stored under the movie unless a test says otherwise.
        every { artworks.findByMovieIdOrderById(any()) } returns emptyList()
    }

    private fun file(
        contentType: String = "image/png",
        bytes: ByteArray = ByteArray(4),
    ): MultipartFile = MockMultipartFile("file", "artwork", contentType, bytes)

    private fun artwork(id: Long = 10, movieId: Long = 1, url: String = "/uploads/artworks/a.png") =
        Artwork(id = id, movieId = movieId, url = url, type = ArtworkType.POSTER)

    // --- store: validation ---

    @Test
    fun `store rejects an empty file and stores nothing`() {
        val failure = assertThrows<IllegalArgumentException> {
            service.store(1L, ArtworkType.POSTER, file(bytes = ByteArray(0)))
        }

        assertThat(failure).hasMessage("Artwork file is empty")
        verify(exactly = 0) { storage.storeArtwork(any(), any()) }
        verify(exactly = 0) { artworks.save(any()) }
    }

    @Test
    fun `store rejects a file larger than ten megabytes`() {
        val oversized = file(bytes = ByteArray(ArtworkService.MAX_BYTES.toInt() + 1))

        val failure = assertThrows<IllegalArgumentException> {
            service.store(1L, ArtworkType.POSTER, oversized)
        }

        assertThat(failure).hasMessage("Artwork file must be at most 10 MB")
        verify(exactly = 0) { storage.storeArtwork(any(), any()) }
    }

    @Test
    fun `store rejects an unsupported content type and stores nothing`() {
        val failure = assertThrows<IllegalArgumentException> {
            service.store(1L, ArtworkType.POSTER, file(contentType = "application/pdf"))
        }

        assertThat(failure).hasMessage("Unsupported artwork type 'application/pdf'. Allowed: PNG, JPEG, WEBP, GIF, AVIF")
        verify(exactly = 0) { storage.storeArtwork(any(), any()) }
    }

    @ParameterizedTest(name = "{0} is stored as {1}")
    @MethodSource("allowedTypes")
    fun `store accepts every allowed image type and picks the matching extension`(
        contentType: String,
        extension: String,
    ) {
        val incoming = file(contentType = contentType)
        val requestedExtension = slot<String>()
        every { storage.storeArtwork(incoming, capture(requestedExtension)) } returns "/uploads/artworks/x$extension"

        val stored = service.store(1L, ArtworkType.POSTER, incoming)

        assertThat(requestedExtension.captured).isEqualTo(extension)
        assertThat(stored.url).isEqualTo("/uploads/artworks/x$extension")
        assertThat(stored.type).isEqualTo(ArtworkType.POSTER)
        assertThat(stored.movieId).isEqualTo(1L)
    }

    // --- store: persistence ---

    @Test
    fun `store writes the file, saves the artwork and makes it the cover`() {
        val incoming = file()
        every { storage.storeArtwork(incoming, ".png") } returns "/uploads/artworks/x.png"
        every { artworks.findByMovieIdOrderById(1L) } returns listOf(artwork(url = "/uploads/artworks/x.png"))

        val stored = service.store(1L, ArtworkType.BACKDROP, incoming)

        assertThat(stored.url).isEqualTo("/uploads/artworks/x.png")
        verify { artworks.save(any()) }
        verify { movies.updateCoverArtwork(1L, "/uploads/artworks/x.png") }
    }

    @Test
    fun `store fails when the movie does not exist and writes no file`() {
        every { movies.get(404L) } throws NoSuchElementException("Movie 404 not found")

        val failure = assertThrows<NoSuchElementException> {
            service.store(404L, ArtworkType.POSTER, file())
        }

        assertThat(failure).hasMessage("Movie 404 not found")
        verify(exactly = 0) { storage.storeArtwork(any(), any()) }
        verify(exactly = 0) { artworks.save(any()) }
    }

    // --- remove ---

    @Test
    fun `remove deletes the row and schedules the file, then promotes the next artwork to cover`() {
        val poster = artwork(id = 10, url = "/uploads/artworks/a.png")
        val backdrop = artwork(id = 11, url = "/uploads/artworks/b.png")
        every { artworks.findById(10L) } returns Optional.of(poster)
        every { artworks.findByMovieIdOrderById(1L) } returns listOf(backdrop)

        service.remove(10L)

        verify { artworks.delete(poster) }
        verify { storage.deleteByUrlAfterCommit("/uploads/artworks/a.png") }
        verify { movies.updateCoverArtwork(1L, "/uploads/artworks/b.png") }
    }

    @Test
    fun `remove clears the cover when it was the movie's last artwork`() {
        val only = artwork(id = 10, url = "/uploads/artworks/a.png")
        every { artworks.findById(10L) } returns Optional.of(only)

        service.remove(10L)

        verify { movies.updateCoverArtwork(1L, null) }
    }

    @Test
    fun `remove fails for an unknown artwork without deleting anything`() {
        every { artworks.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> { service.remove(404L) }

        assertThat(failure).hasMessage("Artwork 404 not found")
        verify(exactly = 0) { artworks.delete(any()) }
        verify(exactly = 0) { storage.deleteByUrlAfterCommit(any()) }
    }

    companion object {

        @JvmStatic
        fun allowedTypes(): List<Arguments> = listOf(
            Arguments.of("image/png", ".png"),
            Arguments.of("image/jpeg", ".jpg"),
            Arguments.of("image/webp", ".webp"),
            Arguments.of("image/gif", ".gif"),
            Arguments.of("image/avif", ".avif"),
            // The check lower-cases the header, so a shouty browser still works.
            Arguments.of("IMAGE/PNG", ".png"),
        )
    }
}
