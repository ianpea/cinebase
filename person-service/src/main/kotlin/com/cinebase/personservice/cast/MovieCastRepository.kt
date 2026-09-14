package com.cinebase.personservice.cast

import org.springframework.data.jpa.repository.JpaRepository

interface MovieCastRepository : JpaRepository<MovieCast, Long> {
    fun findByMovieIdOrderById(movieId: Long): List<MovieCast>
}
