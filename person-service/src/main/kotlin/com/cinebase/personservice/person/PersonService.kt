package com.cinebase.personservice.person

import com.cinebase.personservice.cast.MovieCastRepository
import com.cinebase.personservice.creator.MovieCreatorRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
class PersonService(
    private val people: PersonRepository,
    private val cast: MovieCastRepository,
    private val creators: MovieCreatorRepository,
) {

    fun list(search: String?, page: Int, size: Int): Page<Person> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        return if (search.isNullOrBlank()) {
            people.findAll(pageable)
        } else {
            people.findByNameContainingIgnoreCase(search.trim(), pageable)
        }
    }

    fun get(id: Long): Person =
        people.findById(id).orElseThrow { NoSuchElementException("Person $id not found") }

    fun findAll(ids: Collection<Long>): Map<Long, Person> =
        if (ids.isEmpty()) emptyMap() else people.findAllById(ids).associateBy { it.id }

    @Transactional
    fun create(name: String, biography: String?, birthDate: LocalDate?): Person {
        val trimmed = name.trim()
        requireUnique(trimmed, birthDate, excludingId = null)
        return people.save(Person(name = trimmed, biography = biography?.trim(), birthDate = birthDate))
    }

    @Transactional
    fun update(id: Long, name: String, biography: String?, birthDate: LocalDate?): Person {
        val person = get(id)
        val trimmed = name.trim()
        requireUnique(trimmed, birthDate, excludingId = id)
        person.name = trimmed
        person.biography = biography?.trim()
        person.birthDate = birthDate
        person.updatedAt = Instant.now()
        return people.save(person)
    }

    @Transactional
    fun delete(id: Long) {
        val person = get(id)
        cast.deleteByPersonId(id)
        creators.deleteByPersonId(id)
        people.delete(person)
    }

    /** The trimmed, case-insensitive name plus the birth date identifies a person. */
    private fun requireUnique(name: String, birthDate: LocalDate?, excludingId: Long?) {
        // A null birth date needs its own query: `birth_date = null` never matches in SQL.
        val matches = if (birthDate == null) {
            people.findByNameIgnoreCaseAndBirthDateIsNull(name)
        } else {
            people.findByNameIgnoreCaseAndBirthDate(name, birthDate)
        }
        if (matches.any { it.id != excludingId }) {
            val birth = birthDate?.let { "born $it" } ?: "with no birth date"
            throw IllegalArgumentException("A person named '$name' $birth already exists")
        }
    }

    fun search(query: String, limit: Int = 20): List<Person> =
        people.findByNameContainingIgnoreCase(query.trim(), PageRequest.of(0, limit)).content
}
