package com.cinebase.personservice.creator

import com.cinebase.personservice.person.PersonRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatorService(
    private val creators: MovieCreatorRepository,
    private val people: PersonRepository,
) {

    fun listForMovie(movieId: Long): List<MovieCreator> = creators.findByMovieIdOrderById(movieId)

    @Transactional
    fun add(movieId: Long, personId: Long, job: String): MovieCreator {
        people.findById(personId)
            .orElseThrow { NoSuchElementException("Person $personId not found") }
        return creators.save(MovieCreator(movieId = movieId, personId = personId, job = job.trim()))
    }

    @Transactional
    fun updateJob(id: Long, job: String): MovieCreator {
        val creator = creators.findById(id)
            .orElseThrow { NoSuchElementException("Creator $id not found") }
        creator.job = job.trim()
        return creators.save(creator)
    }

    @Transactional
    fun remove(id: Long) {
        val creator = creators.findById(id)
            .orElseThrow { NoSuchElementException("Creator $id not found") }
        creators.delete(creator)
    }
}
