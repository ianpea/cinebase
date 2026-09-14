package com.cinebase.personservice.creator

import org.springframework.data.jpa.repository.JpaRepository

interface MovieCreatorRepository : JpaRepository<MovieCreator, Long> {
    fun findByMovieIdOrderById(movieId: Long): List<MovieCreator>
}
