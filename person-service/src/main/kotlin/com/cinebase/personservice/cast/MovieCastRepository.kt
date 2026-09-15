package com.cinebase.personservice.cast

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MovieCastRepository : JpaRepository<MovieCast, Long> {
    fun findByMovieIdOrderById(movieId: Long): List<MovieCast>

    @Modifying
    @Query("delete from MovieCast c where c.personId = :personId")
    fun deleteByPersonId(@Param("personId") personId: Long): Int
}
