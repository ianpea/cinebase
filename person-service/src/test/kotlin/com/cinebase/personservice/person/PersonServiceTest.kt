package com.cinebase.personservice.person

import com.cinebase.personservice.cast.MovieCastRepository
import com.cinebase.personservice.creator.MovieCreatorRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

/**
 * Domain rules of [PersonService] in isolation.
 *
 * Only behaviour that lives *in the service* is asserted here: trimming, the existence checks, the
 * pagination clamp, the blank-vs-present search branch and the delete order. Plain repository
 * delegation (and the end-to-end paths) are covered by `PersonServiceGrpcIntegrationTest`, so they
 * are not repeated.
 */
class PersonServiceTest {

    // relaxedUnitFun so Unit-returning repository methods (delete/deleteAll) need no stubbing.
    private val people = mockk<PersonRepository>(relaxUnitFun = true)
    private val cast = mockk<MovieCastRepository>()
    private val creators = mockk<MovieCreatorRepository>()

    private val service = PersonService(people, cast, creators)

    @BeforeEach
    fun stubSaving() {
        every { people.save(any()) } returnsArgument 0
    }

    private fun person(id: Long = 1, name: String = "Christopher Nolan") = Person(id = id, name = name)

    private fun pageOf(vararg content: Person): Page<Person> = mockk<Page<Person>>().apply {
        every { this@apply.content } returns content.toList()
    }

    // --- create ---

    @Test
    fun `create trims the name and biography`() {
        val created = service.create("  Christopher Nolan  ", "  British director  ", LocalDate.of(1970, 7, 30))

        assertThat(created.name).isEqualTo("Christopher Nolan")
        assertThat(created.biography).isEqualTo("British director")
        assertThat(created.birthDate).isEqualTo(LocalDate.of(1970, 7, 30))
        verify { people.save(any()) }
    }

    // --- get / find ---

    @Test
    fun `get fails for an unknown person`() {
        every { people.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> { service.get(404L) }

        assertThat(failure).hasMessage("Person 404 not found")
    }

    @Test
    fun `findAll short-circuits an empty id collection without querying the database`() {
        assertThat(service.findAll(emptyList())).isEmpty()

        verify(exactly = 0) { people.findAllById(any<Collection<Long>>()) }
    }

    @Test
    fun `findAll keys the people by id and omits ids that no longer exist`() {
        every { people.findAllById(listOf(1L, 2L, 3L)) } returns listOf(person(1, "Ana"), person(2, "Bob"))

        val found = service.findAll(listOf(1L, 2L, 3L))

        assertThat(found).containsOnlyKeys(1L, 2L)
        assertThat(found[2L]?.name).isEqualTo("Bob")
    }

    // --- update ---

    @Test
    fun `update rewrites every mutable field and bumps updatedAt`() {
        val existing = person().apply { updatedAt = Instant.parse("2020-01-01T00:00:00Z") }
        every { people.findById(1L) } returns Optional.of(existing)

        val updated = service.update(1L, "  Sir Christopher Nolan  ", "  update  ", LocalDate.of(1970, 7, 30))

        assertThat(updated.name).isEqualTo("Sir Christopher Nolan")
        assertThat(updated.biography).isEqualTo("update")
        assertThat(updated.birthDate).isEqualTo(LocalDate.of(1970, 7, 30))
        assertThat(updated.updatedAt).isAfter(Instant.parse("2020-01-01T00:00:00Z"))
    }

    @Test
    fun `update clears optional fields when they are omitted`() {
        val existing = person().apply {
            biography = "old"
            birthDate = LocalDate.of(1970, 7, 30)
        }
        every { people.findById(1L) } returns Optional.of(existing)

        val updated = service.update(1L, "Christopher Nolan", null, null)

        assertThat(updated.biography).isNull()
        assertThat(updated.birthDate).isNull()
    }

    @Test
    fun `update fails for an unknown person without saving`() {
        every { people.findById(404L) } returns Optional.empty()

        assertThrows<NoSuchElementException> { service.update(404L, "Nobody", null, null) }

        verify(exactly = 0) { people.save(any()) }
    }

    // --- delete ---

    @Test
    fun `delete removes the person's roles first and the person last`() {
        val existing = person()
        every { people.findById(1L) } returns Optional.of(existing)
        every { cast.deleteByPersonId(1L) } returns 2
        every { creators.deleteByPersonId(1L) } returns 1

        service.delete(1L)

        verifyOrder {
            cast.deleteByPersonId(1L)
            creators.deleteByPersonId(1L)
            people.delete(existing)
        }
    }

    @Test
    fun `delete fails for an unknown person without deleting any roles`() {
        every { people.findById(404L) } returns Optional.empty()

        val failure = assertThrows<NoSuchElementException> { service.delete(404L) }

        assertThat(failure).hasMessage("Person 404 not found")
        verify(exactly = 0) { cast.deleteByPersonId(any()) }
        verify(exactly = 0) { creators.deleteByPersonId(any()) }
        verify(exactly = 0) { people.delete(any()) }
    }

    // --- list: pagination clamp ---

    @ParameterizedTest(name = "page={0}, size={1} is clamped to page={2}, size={3}")
    @MethodSource("paginationClamping")
    fun `list clamps the requested page and size into the supported range`(
        requestedPage: Int,
        requestedSize: Int,
        expectedPage: Int,
        expectedSize: Int,
    ) {
        val pageable = slot<Pageable>()
        every { people.findAll(capture(pageable)) } returns pageOf()

        service.list(search = null, page = requestedPage, size = requestedSize)

        assertThat(pageable.captured.pageNumber).isEqualTo(expectedPage)
        assertThat(pageable.captured.pageSize).isEqualTo(expectedSize)
    }

    // --- list: search branch ---

    @Test
    fun `list forwards a trimmed search term and leaves case matching to the database`() {
        every { people.findByNameContainingIgnoreCase("NOLAN", any<Pageable>()) } returns pageOf(person())

        val result = service.list(search = "  NOLAN  ", page = 0, size = 20)

        assertThat(result.content).hasSize(1)
        verify { people.findByNameContainingIgnoreCase("NOLAN", any()) }
    }

    @ParameterizedTest(name = "search=[{0}]")
    @MethodSource("blankSearchTerms")
    fun `list returns every person when the search term is blank or absent`(search: String?) {
        every { people.findAll(any<Pageable>()) } returns pageOf(person())

        val result = service.list(search = search, page = 0, size = 20)

        assertThat(result.content).hasSize(1)
        verify(exactly = 0) { people.findByNameContainingIgnoreCase(any(), any()) }
    }

    // --- search ---

    @Test
    fun `search trims the term and applies the limit to the first page`() {
        val pageable = slot<Pageable>()
        every { people.findByNameContainingIgnoreCase("ana", capture(pageable)) } returns pageOf(person(1, "Ana"))

        val found = service.search("  ana  ", limit = 5)

        assertThat(found).hasSize(1)
        assertThat(pageable.captured.pageNumber).isEqualTo(0)
        assertThat(pageable.captured.pageSize).isEqualTo(5)
    }

    @Test
    fun `search defaults to twenty results`() {
        val pageable = slot<Pageable>()
        every { people.findByNameContainingIgnoreCase("ana", capture(pageable)) } returns pageOf()

        service.search("ana")

        assertThat(pageable.captured.pageSize).isEqualTo(20)
    }

    companion object {

        @JvmStatic
        fun paginationClamping(): List<Arguments> = listOf(
            // A negative page starts from the first page.
            Arguments.of(-4, 20, 0, 20),
            // A zero size would make Spring Data throw, so it becomes the minimum of one.
            Arguments.of(3, 0, 3, 1),
            // An oversized page is capped.
            Arguments.of(3, 5_000, 3, 100),
            // The boundary value passes through untouched.
            Arguments.of(0, 100, 0, 100),
        )

        @JvmStatic
        fun blankSearchTerms(): List<String?> = listOf(null, "", "   ")
    }
}
