package com.cinebase.personservice.cast

import com.cinebase.personservice.person.PersonRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CastService(
    private val cast: MovieCastRepository,
    private val people: PersonRepository,
) {

    fun listForMovie(movieId: Long): List<MovieCast> = cast.findByMovieIdOrderById(movieId)

    @Transactional
    fun add(movieId: Long, personId: Long, characterName: String): MovieCast {
        people.findById(personId)
            .orElseThrow { NoSuchElementException("Person $personId not found") }
        return cast.save(MovieCast(movieId = movieId, personId = personId, characterName = characterName.trim()))
    }

    @Transactional
    fun updateCharacterName(id: Long, characterName: String): MovieCast {
        val member = cast.findById(id)
            .orElseThrow { NoSuchElementException("Cast member $id not found") }
        member.characterName = characterName.trim()
        return cast.save(member)
    }

    @Transactional
    fun remove(id: Long) {
        val member = cast.findById(id)
            .orElseThrow { NoSuchElementException("Cast member $id not found") }
        cast.delete(member)
    }
}
