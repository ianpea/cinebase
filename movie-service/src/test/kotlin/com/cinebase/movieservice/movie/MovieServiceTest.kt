package com.cinebase.movieservice.movie

import com.cinebase.movieservice.artwork.Artwork
import com.cinebase.movieservice.artwork.ArtworkRepository
import com.cinebase.movieservice.artwork.ArtworkType
import com.cinebase.movieservice.config.UploadStorage
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
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.time.Instant
import java.util.Optional

/**
 * Domain rules of [MovieService] in isolation.
 *
 * Only behaviour that lives *in the service* is asserted here: text trimming, the existence
 * checks, the pagination clamp, the blank-vs-present search branch and the order in which
 * artwork is cleaned up. Plain repository delegation and the end-to-end paths are covered by
 * `MovieServiceGraphQlIntegrationTest`.
 */
class MovieServiceTest {

    // relaxedUnitFun so Unit-returning repository methods (delete/deleteByMovieId) need no stubbing.
    private val movies = mockk<MovieRepository>(relaxUnitFun = true)
    private val artworks = mockk<ArtworkRepository>(relaxUnitFun = true)
    private val storage = mockk<UploadStorage>(relaxUnitFun = true)

    private val service = MovieService(movies, artworks, storage)

    @BeforeEach
    fun stubSaving() {
        every { movies.save(any()) } returnsArgument 0
    }

    private fun movie(id: Long = 1, title: String = "Interstellar") = Movie(id = id, title = title)

    private fun pageOf(vararg content: Movie): Page<Movie> = mockk<Page<Movie>>().apply {
        every { this@apply.content } returns content.toList()
    }

    // --- create ---

    @Test
    fun `create trims every text field`() {
        val created = service.create("  Interstellar  ", "  A trip through a wormhole.  ", 2014, "  Sci-Fi  ")

        assertThat(created.title).isEqualTo("Interstellar")
        assertThat(created.synopsis).isEqualTo("A trip through a wormhole.")
        assertThat(created.releaseYear).isEqualTo(2014)
        assertThat(created.genre).isEqualTo("Sci-Fi")
        verify { movies.save(any()) }
    }

    // --- get / find ---

    @Test
    fun `get fails for an unknown movie`() {
        every { movies.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> { service.get(404L) }

        assertThat(failure).hasMessage("Movie 404 not found")
    }

    @Test
    fun `find returns null instead of failing for an unknown movie`() {
        every { movies.findById(404L) } returns Optional.empty()

        assertThat(service.find(404L)).isNull()
    }

    // --- update ---

    @Test
    fun `update rewrites every mutable field and bumps updatedAt`() {
        val existing = movie().apply { updatedAt = Instant.parse("2020-01-01T00:00:00Z") }
        every { movies.findById(1L) } returns Optional.of(existing)

        val updated = service.update(1L, "  Interstellar  ", "  Murph!  ", 2014, "  Sci-Fi  ")

        assertThat(updated.title).isEqualTo("Interstellar")
        assertThat(updated.synopsis).isEqualTo("Murph!")
        assertThat(updated.releaseYear).isEqualTo(2014)
        assertThat(updated.genre).isEqualTo("Sci-Fi")
        assertThat(updated.updatedAt).isAfter(Instant.parse("2020-01-01T00:00:00Z"))
    }

    @Test
    fun `update clears optional fields when they are omitted`() {
        val existing = movie().apply {
            synopsis = "old"
            genre = "old"
            releaseYear = 1999
        }
        every { movies.findById(1L) } returns Optional.of(existing)

        val updated = service.update(1L, "Interstellar", null, null, null)

        assertThat(updated.synopsis).isNull()
        assertThat(updated.genre).isNull()
        assertThat(updated.releaseYear).isNull()
    }

    @Test
    fun `update fails for an unknown movie without saving`() {
        every { movies.findById(404L) } returns Optional.empty()

        assertThrows<NoSuchElementException> { service.update(404L, "Nobody", null, null, null) }

        verify(exactly = 0) { movies.save(any()) }
    }

    // --- delete ---

    @Test
    fun `delete removes the stored files, the artwork rows and the movie`() {
        val movie = movie()
        every { movies.findById(1L) } returns Optional.of(movie)
        every { artworks.findByMovieIdOrderById(1L) } returns listOf(
            Artwork(id = 10, movieId = 1, url = "/uploads/artworks/a.png", type = ArtworkType.POSTER),
            Artwork(id = 11, movieId = 1, url = "/uploads/artworks/b.png", type = ArtworkType.BACKDROP),
        )
        val deleted = mutableListOf<String>()
        every { storage.deleteByUrl(capture(deleted)) } returns true

        service.delete(1L)

        // Files are removed before their rows are dropped, and every artwork is cleaned up.
        assertThat(deleted).containsExactly("/uploads/artworks/a.png", "/uploads/artworks/b.png")
        verify { artworks.deleteByMovieId(1L) }
        verify { movies.delete(movie) }
    }

    @Test
    fun `delete fails for an unknown movie without touching artwork`() {
        every { movies.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> { service.delete(404L) }

        assertThat(failure).hasMessage("Movie 404 not found")
        verify(exactly = 0) { artworks.deleteByMovieId(any()) }
        verify(exactly = 0) { movies.delete(any()) }
    }

    // --- list: pagination clamp ---

    @ParameterizedTest(name = "page={0}, size={1} is clamped to page={2}, size={3}")
    @MethodSource("paginationClamping")
    fun `list clamps the requested page and size into the supported range`(
        requestedPage: Int,
        requestedSize: Int,
        expectedPage: Int,
        expectedSize: Int,
    ) {
        val pageable = slot<Pageable>()
        every { movies.findAll(capture(pageable)) } returns pageOf()

        service.list(search = null, sort = DEFAULT_SORT, page = requestedPage, size = requestedSize)

        assertThat(pageable.captured.pageNumber).isEqualTo(expectedPage)
        assertThat(pageable.captured.pageSize).isEqualTo(expectedSize)
    }

    // --- list: search branch ---

    @Test
    fun `list forwards a trimmed search term and leaves case matching to the database`() {
        every { movies.findByTitleContainingIgnoreCase("STELLAR", any<Pageable>()) } returns pageOf(movie())

        val result = service.list(search = "  STELLAR  ", sort = DEFAULT_SORT, page = 0, size = 12)

        assertThat(result.content).hasSize(1)
        verify { movies.findByTitleContainingIgnoreCase("STELLAR", any()) }
    }

    @ParameterizedTest(name = "search=[{0}]")
    @MethodSource("blankSearchTerms")
    fun `list returns every movie when the search term is blank or absent`(search: String?) {
        every { movies.findAll(any<Pageable>()) } returns pageOf(movie())

        val result = service.list(search = search, sort = DEFAULT_SORT, page = 0, size = 12)

        assertThat(result.content).hasSize(1)
        verify(exactly = 0) { movies.findByTitleContainingIgnoreCase(any(), any()) }
    }

    @Test
    fun `list applies the requested sort to the query`() {
        val pageable = slot<Pageable>()
        every { movies.findAll(capture(pageable)) } returns pageOf()

        service.list(search = null, sort = DEFAULT_SORT, page = 0, size = 12)

        assertThat(pageable.captured.sort.getOrderFor("createdAt")?.direction).isEqualTo(Sort.Direction.DESC)
    }

    // --- search ---

    @Test
    fun `search trims the term and applies the limit to the first page`() {
        val pageable = slot<Pageable>()
        every { movies.findByTitleContainingIgnoreCase("inter", capture(pageable)) } returns pageOf(movie())

        val found = service.search("  inter  ", limit = 5)

        assertThat(found).hasSize(1)
        assertThat(pageable.captured.pageNumber).isEqualTo(0)
        assertThat(pageable.captured.pageSize).isEqualTo(5)
    }

    @Test
    fun `search defaults to twenty results`() {
        val pageable = slot<Pageable>()
        every { movies.findByTitleContainingIgnoreCase("inter", capture(pageable)) } returns pageOf()

        service.search("inter")

        assertThat(pageable.captured.pageSize).isEqualTo(20)
    }

    // --- cover artwork ---

    @Test
    fun `updateCoverArtwork stores the url and bumps updatedAt`() {
        val existing = movie().apply { updatedAt = Instant.parse("2020-01-01T00:00:00Z") }
        every { movies.findById(1L) } returns Optional.of(existing)

        service.updateCoverArtwork(1L, "/uploads/artworks/a.png")

        assertThat(existing.artworkUrl).isEqualTo("/uploads/artworks/a.png")
        assertThat(existing.updatedAt).isAfter(Instant.parse("2020-01-01T00:00:00Z"))
        verify { movies.save(existing) }
    }

    @Test
    fun `updateCoverArtwork ignores an unknown movie`() {
        every { movies.findById(404L) } returns Optional.empty()

        service.updateCoverArtwork(404L, "/uploads/artworks/a.png")

        verify(exactly = 0) { movies.save(any()) }
    }

    companion object {

        private val DEFAULT_SORT: Sort = Sort.by(Sort.Direction.DESC, "createdAt")

        @JvmStatic
        fun paginationClamping(): List<Arguments> = listOf(
            // A negative page starts from the first page.
            Arguments.of(-4, 12, 0, 12),
            // A zero size would make Spring Data throw, so it becomes the minimum of one.
            Arguments.of(3, 0, 3, 1),
            // An oversized page is capped at the maximum.
            Arguments.of(3, 5_000, 3, 100),
            // The boundary value passes through untouched.
            Arguments.of(0, 100, 0, 100),
        )

        @JvmStatic
        fun blankSearchTerms(): List<String?> = listOf(null, "", "   ")
    }
}
