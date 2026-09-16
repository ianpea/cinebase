package com.cinebase.personservice.cast

import com.cinebase.personservice.person.Person
import com.cinebase.personservice.person.PersonRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

/**
 * Domain rules of [CastService]: text trimming, the person-existence check and the deliberately
 * absent movie check. Plain repository delegation is covered by `PersonServiceGrpcIntegrationTest`.
 */
class CastServiceTest {

    // relaxedUnitFun so Unit-returning repository methods (delete/deleteAll) need no stubbing.
    private val cast = mockk<MovieCastRepository>(relaxUnitFun = true)
    private val people = mockk<PersonRepository>(relaxUnitFun = true)

    private val service = CastService(cast, people)

    @BeforeEach
    fun stubSaving() {
        every { cast.save(any()) } returnsArgument 0
    }

    private fun person(id: Long = 7) = Person(id = id, name = "Matthew McConaughey")

    private fun member(id: Long = 1, movieId: Long = 1, personId: Long = 7, character: String = "Cooper") =
        MovieCast(id = id, movieId = movieId, personId = personId, characterName = character)

    @Test
    fun `add stores the cast member with a trimmed character name`() {
        every { people.findById(7L) } returns Optional.of(person())

        val added = service.add(movieId = 1L, personId = 7L, characterName = "  Cooper  ")

        assertThat(added.movieId).isEqualTo(1L)
        assertThat(added.personId).isEqualTo(7L)
        assertThat(added.characterName).isEqualTo("Cooper")
        verify { cast.save(any()) }
    }

    @Test
    fun `add fails when the person does not exist and stores nothing`() {
        every { people.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> {
            service.add(movieId = 1L, personId = 404L, characterName = "Nobody")
        }

        assertThat(failure).hasMessage("Person 404 not found")
        verify(exactly = 0) { cast.save(any()) }
    }

    @Test
    fun `add accepts any movie id because movies are owned by movie-service`() {
        every { people.findById(7L) } returns Optional.of(person())

        // person-service only stores the id and never validates it against another service's data.
        val added = service.add(movieId = 999L, personId = 7L, characterName = "Cooper")

        assertThat(added.movieId).isEqualTo(999L)
    }

    @Test
    fun `updateCharacterName rewrites the stored character name with trimming`() {
        val stored = member()
        every { cast.findById(1L) } returns Optional.of(stored)

        val updated = service.updateCharacterName(1L, "  Joseph Cooper  ")

        assertThat(updated.characterName).isEqualTo("Joseph Cooper")
        verify { cast.save(stored) }
    }

    @Test
    fun `updateCharacterName fails for a missing cast member`() {
        every { cast.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> { service.updateCharacterName(404L, "Cooper") }

        assertThat(failure).hasMessage("Cast member 404 not found")
        verify(exactly = 0) { cast.save(any()) }
    }

    @Test
    fun `remove fails for a missing cast member without deleting`() {
        every { cast.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> { service.remove(404L) }

        assertThat(failure).hasMessage("Cast member 404 not found")
        verify(exactly = 0) { cast.delete(any()) }
    }
}
