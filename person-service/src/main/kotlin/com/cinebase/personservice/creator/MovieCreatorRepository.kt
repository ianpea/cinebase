package com.cinebase.personservice.creator

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MovieCreatorRepository : JpaRepository<MovieCreator, Long> {
    fun findByMovieIdOrderById(movieId: Long): List<MovieCreator>

    @Modifying
    @Query("delete from MovieCreator c where c.personId = :personId")
    fun deleteByPersonId(@Param("personId") personId: Long): Int
}
