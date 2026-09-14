package com.cinebase.movieservice.artwork

import com.cinebase.movieservice.movie.MovieService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArtworkService(
    private val artworks: ArtworkRepository,
    private val movies: MovieService,
) {

    fun listForMovie(movieId: Long): List<Artwork> = artworks.findByMovieIdOrderById(movieId)

    @Transactional
    fun remove(id: Long) {
        val artwork = artworks.findById(id)
            .orElseThrow { NoSuchElementException("Artwork $id not found") }
        artworks.delete(artwork)
    }

    @Transactional
    fun register(movieId: Long, url: String, type: ArtworkType): Artwork {
        movies.get(movieId) // ensure the movie exists
        return artworks.save(Artwork(movieId = movieId, url = url, type = type))
    }
}
