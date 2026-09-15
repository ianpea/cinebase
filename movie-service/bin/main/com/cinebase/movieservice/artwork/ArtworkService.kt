package com.cinebase.movieservice.artwork

import com.cinebase.movieservice.config.UploadStorage
import com.cinebase.movieservice.movie.MovieService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class ArtworkService(
    private val artworks: ArtworkRepository,
    private val movies: MovieService,
    private val storage: UploadStorage,
) {

    fun listForMovie(movieId: Long): List<Artwork> = artworks.findByMovieIdOrderById(movieId)

    @Transactional
    fun remove(id: Long) {
        val artwork = artworks.findById(id)
            .orElseThrow { NoSuchElementException("Artwork $id not found") }
        artworks.delete(artwork)
        syncCoverArtwork(artwork.movieId)
        storage.deleteByUrlAfterCommit(artwork.url)
    }

    @Transactional
    fun register(movieId: Long, url: String, type: ArtworkType): Artwork {
        movies.get(movieId) // ensure the movie exists
        return artworks.save(Artwork(movieId = movieId, url = url, type = type))
    }

    /** Validates, stores and registers an uploaded artwork file. */
    @Transactional
    fun store(movieId: Long, type: ArtworkType, file: MultipartFile): Artwork {
        val extension = validate(file)
        movies.get(movieId) // ensure the movie exists
        val url = storage.storeArtwork(file, extension)
        val artwork = artworks.save(Artwork(movieId = movieId, url = url, type = type))
        syncCoverArtwork(movieId)
        return artwork
    }

    /** Returns the file extension for an allowed image, or fails with a user-facing message. */
    private fun validate(file: MultipartFile): String {
        if (file.isEmpty) {
            throw IllegalArgumentException("Artwork file is empty")
        }
        if (file.size > MAX_BYTES) {
            throw IllegalArgumentException("Artwork file must be at most ${MAX_BYTES / (1024 * 1024)} MB")
        }
        val contentType = file.contentType?.lowercase()
        return ALLOWED_TYPES[contentType]
            ?: throw IllegalArgumentException(
                "Unsupported artwork type '${file.contentType}'. Allowed: PNG, JPEG, WEBP, GIF, AVIF",
            )
    }

    private fun syncCoverArtwork(movieId: Long) {
        movies.updateCoverArtwork(movieId, artworks.findByMovieIdOrderById(movieId).firstOrNull()?.url)
    }

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024

        val ALLOWED_TYPES = mapOf(
            "image/png" to ".png",
            "image/jpeg" to ".jpg",
            "image/webp" to ".webp",
            "image/gif" to ".gif",
            "image/avif" to ".avif",
        )
    }
}
