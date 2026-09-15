package com.cinebase.personservice.person

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface PersonRepository : JpaRepository<Person, Long> {
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Person>

    fun findByNameIgnoreCaseAndBirthDate(name: String, birthDate: LocalDate): List<Person>

    fun findByNameIgnoreCaseAndBirthDateIsNull(name: String): List<Person>
}
