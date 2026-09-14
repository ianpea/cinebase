package com.cinebase.movieservice.artwork

import org.springframework.data.jpa.repository.JpaRepository

interface ArtworkRepository : JpaRepository<Artwork, Long> {
    fun findByMovieIdOrderById(movieId: Long): List<Artwork>
    fun deleteByMovieId(movieId: Long)
}
