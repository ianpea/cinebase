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
    fun create(name: String, biography: String?, birthDate: LocalDate?): Person =
        people.save(Person(name = name.trim(), biography = biography?.trim(), birthDate = birthDate))

    @Transactional
    fun update(id: Long, name: String, biography: String?, birthDate: LocalDate?): Person {
        val person = get(id)
        person.name = name.trim()
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

    fun search(query: String, limit: Int = 20): List<Person> =
        people.findByNameContainingIgnoreCase(query.trim(), PageRequest.of(0, limit)).content
}
