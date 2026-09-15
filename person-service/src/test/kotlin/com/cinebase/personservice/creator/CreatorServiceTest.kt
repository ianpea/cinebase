package com.cinebase.personservice.creator

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
 * Domain rules of [CreatorService]. Mirrors `CastServiceTest` for the creator side: trimming, the
 * person-existence check and the "one person, several jobs" allowance. Plain repository delegation
 * is covered by `PersonServiceGrpcIntegrationTest`.
 */
class CreatorServiceTest {

    // relaxedUnitFun so Unit-returning repository methods (delete/deleteAll) need no stubbing.
    private val creators = mockk<MovieCreatorRepository>(relaxUnitFun = true)
    private val people = mockk<PersonRepository>(relaxUnitFun = true)

    private val service = CreatorService(creators, people)

    @BeforeEach
    fun stubSaving() {
        every { creators.save(any()) } returnsArgument 0
    }

    private fun person(id: Long = 8) = Person(id = id, name = "Christopher Nolan")

    private fun creator(id: Long = 1, movieId: Long = 1, personId: Long = 8, job: String = "Director") =
        MovieCreator(id = id, movieId = movieId, personId = personId, job = job)

    @Test
    fun `add stores the creator with a trimmed job`() {
        every { people.findById(8L) } returns Optional.of(person())

        val added = service.add(movieId = 1L, personId = 8L, job = "  Director  ")

        assertThat(added.movieId).isEqualTo(1L)
        assertThat(added.personId).isEqualTo(8L)
        assertThat(added.job).isEqualTo("Director")
        verify { creators.save(any()) }
    }

    @Test
    fun `add fails when the person does not exist and stores nothing`() {
        every { people.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> {
            service.add(movieId = 1L, personId = 404L, job = "Director")
        }

        assertThat(failure).hasMessage("Person 404 not found")
        verify(exactly = 0) { creators.save(any()) }
    }

    @Test
    fun `add allows one person to hold several jobs on the same movie`() {
        every { people.findById(8L) } returns Optional.of(person())

        val director = service.add(1L, 8L, "Director")
        val producer = service.add(1L, 8L, "Producer")

        assertThat(director.job).isEqualTo("Director")
        assertThat(producer.job).isEqualTo("Producer")
        verify(exactly = 2) { creators.save(any()) }
    }

    @Test
    fun `updateJob rewrites the stored job with trimming`() {
        val stored = creator()
        every { creators.findById(1L) } returns Optional.of(stored)

        val updated = service.updateJob(1L, "  Executive Producer  ")

        assertThat(updated.job).isEqualTo("Executive Producer")
        verify { creators.save(stored) }
    }

    @Test
    fun `updateJob fails for a missing creator`() {
        every { creators.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> { service.updateJob(404L, "Director") }

        assertThat(failure).hasMessage("Creator 404 not found")
        verify(exactly = 0) { creators.save(any()) }
    }

    @Test
    fun `remove fails for a missing creator without deleting`() {
        every { creators.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> { service.remove(404L) }

        assertThat(failure).hasMessage("Creator 404 not found")
        verify(exactly = 0) { creators.delete(any()) }
    }
}
