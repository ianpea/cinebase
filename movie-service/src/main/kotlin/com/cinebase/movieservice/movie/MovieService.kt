package com.cinebase.movieservice.movie

import com.cinebase.movieservice.artwork.ArtworkRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class MovieService(
    private val movies: MovieRepository,
    private val artworks: ArtworkRepository,
) {

    fun list(search: String?, page: Int, size: Int): Page<Movie> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        return if (search.isNullOrBlank()) {
            movies.findAll(pageable)
        } else {
            movies.findByTitleContainingIgnoreCase(search.trim(), pageable)
        }
    }

    fun get(id: Long): Movie =
        movies.findById(id).orElseThrow { NoSuchElementException("Movie $id not found") }

    @Transactional
    fun create(title: String, synopsis: String?, releaseYear: Int?, genre: String?): Movie {
        val movie = Movie(
            title = title.trim(),
            synopsis = synopsis?.trim(),
            releaseYear = releaseYear,
            genre = genre?.trim(),
        )
        return movies.save(movie)
    }

    @Transactional
    fun update(id: Long, title: String, synopsis: String?, releaseYear: Int?, genre: String?): Movie {
        val movie = get(id)
        movie.title = title.trim()
        movie.synopsis = synopsis?.trim()
        movie.releaseYear = releaseYear
        movie.genre = genre?.trim()
        movie.updatedAt = Instant.now()
        return movies.save(movie)
    }

    @Transactional
    fun delete(id: Long) {
        val movie = get(id)
        artworks.deleteByMovieId(id)
        movies.delete(movie)
    }

    fun search(query: String, limit: Int = 20): List<Movie> =
        movies.findByTitleContainingIgnoreCase(query.trim(), PageRequest.of(0, limit)).content
}
