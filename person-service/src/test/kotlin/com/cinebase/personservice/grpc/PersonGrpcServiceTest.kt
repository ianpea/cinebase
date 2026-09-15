package com.cinebase.personservice.grpc

import com.cinebase.person.v1.AddCastMemberRequest
import com.cinebase.person.v1.AddCreatorRequest
import com.cinebase.person.v1.CreatePersonRequest
import com.cinebase.person.v1.DeletePersonRequest
import com.cinebase.person.v1.GetPeopleForMovieRequest
import com.cinebase.person.v1.GetPersonRequest
import com.cinebase.person.v1.PersonServiceGrpc
import com.cinebase.person.v1.RemoveCastMemberRequest
import com.cinebase.person.v1.RemoveCreatorRequest
import com.cinebase.person.v1.SearchPeopleRequest
import com.cinebase.person.v1.UpdateCastMemberRequest
import com.cinebase.person.v1.UpdateCreatorRequest
import com.cinebase.person.v1.UpdatePersonRequest
import com.cinebase.personservice.cast.CastService
import com.cinebase.personservice.cast.MovieCast
import com.cinebase.personservice.creator.CreatorService
import com.cinebase.personservice.creator.MovieCreator
import com.cinebase.personservice.person.Person
import com.cinebase.personservice.person.PersonService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.data.domain.Page
import java.time.Instant
import java.time.LocalDate

/** The mocked collaborators a parameterized case can arrange before invoking an RPC. */
class GrpcCollaborators(
    val people: PersonService,
    val cast: CastService,
    val creators: CreatorService,
)

/**
 * One RPC call plus the arrangement it needs, so several status-mapping cases can share a single
 * test body. [toString] becomes the JUnit display name.
 */
class RpcCase(
    private val label: String,
    val expectedMessage: String,
    val arrange: (GrpcCollaborators) -> Unit,
    val invoke: (PersonServiceGrpc.PersonServiceBlockingStub) -> Unit,
) {
    override fun toString(): String = label
}

/**
 * The gRPC boundary of person-service, with the service layer mocked.
 *
 * The happy paths are covered end-to-end by `PersonServiceGrpcIntegrationTest`; this suite exists
 * for what only the boundary can show: the protobuf↔domain mapping (ISO timestamps, and the
 * empty-string standing in for an absent optional field), the batched person lookup, and the
 * status contract (`NOT_FOUND` / `INVALID_ARGUMENT` / `INTERNAL`) that movie-service turns back
 * into GraphQL errors.
 */
class PersonGrpcServiceTest {

    private val personService = mockk<PersonService>()
    private val castService = mockk<CastService>()
    private val creatorService = mockk<CreatorService>()

    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var stub: PersonServiceGrpc.PersonServiceBlockingStub

    private val collaborators get() = GrpcCollaborators(personService, castService, creatorService)

    @BeforeEach
    fun startServer() {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(PersonGrpcService(personService, castService, creatorService))
            .build()
            .start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        stub = PersonServiceGrpc.newBlockingStub(channel)
    }

    @AfterEach
    fun stopServer() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    private fun person(
        id: Long = 1,
        name: String = "Christopher Nolan",
        biography: String? = "British director",
        birthDate: LocalDate? = LocalDate.of(1970, 7, 30),
    ) = Person(
        id = id,
        name = name,
        biography = biography,
        birthDate = birthDate,
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2024-01-02T00:00:00Z"),
    )

    private fun pageOf(people: List<Person>, total: Long = people.size.toLong()): Page<Person> =
        mockk<Page<Person>>().apply {
            every { content } returns people
            every { totalElements } returns total
        }

    // --- protobuf mapping ---

    @Test
    fun `getPerson maps the entity onto the proto message with ISO-8601 timestamps`() {
        every { personService.get(1L) } returns person()

        val response = stub.getPerson(GetPersonRequest.newBuilder().setId(1L).build())

        assertThat(response.id).isEqualTo(1L)
        assertThat(response.name).isEqualTo("Christopher Nolan")
        assertThat(response.biography).isEqualTo("British director")
        assertThat(response.birthDate).isEqualTo("1970-07-30")
        assertThat(response.createdAt).isEqualTo("2024-01-01T00:00:00Z")
        assertThat(response.updatedAt).isEqualTo("2024-01-02T00:00:00Z")
    }

    @Test
    fun `getPerson uses empty strings for absent optional fields because proto3 has no null`() {
        every { personService.get(1L) } returns person(biography = null, birthDate = null)

        val response = stub.getPerson(GetPersonRequest.newBuilder().setId(1L).build())

        assertThat(response.biography).isEmpty()
        assertThat(response.birthDate).isEmpty()
    }

    @Test
    fun `searchPeople maps the page contents and the total count`() {
        val results = listOf(person(), person(2, "Jonathan Nolan"))
        every { personService.list("nolan", 0, 10) } returns pageOf(results, total = 42)

        val response = stub.searchPeople(
            SearchPeopleRequest.newBuilder().setQuery("nolan").setPage(0).setSize(10).build(),
        )

        assertThat(response.peopleList.map { it.name }).containsExactly("Christopher Nolan", "Jonathan Nolan")
        assertThat(response.total).isEqualTo(42L)
    }

    @Test
    fun `getPeopleForMovie looks up each person only once`() {
        every { castService.listForMovie(42L) } returns listOf(
            MovieCast(id = 1, movieId = 42, personId = 7, characterName = "Cooper"),
            MovieCast(id = 2, movieId = 42, personId = 7, characterName = "Older Cooper"),
        )
        every { creatorService.listForMovie(42L) } returns listOf(
            MovieCreator(id = 3, movieId = 42, personId = 8, job = "Director"),
        )
        every { personService.findAll(setOf(7L, 8L)) } returns mapOf(
            7L to person(7, "Matthew McConaughey"),
            8L to person(8, "Christopher Nolan"),
        )

        val response = stub.getPeopleForMovie(GetPeopleForMovieRequest.newBuilder().setMovieId(42L).build())

        assertThat(response.castCount).isEqualTo(2)
        assertThat(response.creatorsCount).isEqualTo(1)
        // The duplicate actor must not cause a second round trip.
        verify(exactly = 1) { personService.findAll(setOf(7L, 8L)) }
    }

    // --- status contract ---

    @ParameterizedTest(name = "{0} maps a missing record to NOT_FOUND")
    @MethodSource("notFoundCases")
    fun `a missing record is reported as NOT_FOUND`(case: RpcCase) {
        case.arrange(collaborators)

        val failure = assertThrows<StatusRuntimeException> { case.invoke(stub) }

        assertThat(failure.status.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(failure.status.description).isEqualTo(case.expectedMessage)
    }

    @ParameterizedTest(name = "{0} maps invalid input to INVALID_ARGUMENT")
    @MethodSource("invalidArgumentCases")
    fun `invalid input is reported as INVALID_ARGUMENT`(case: RpcCase) {
        case.arrange(collaborators)

        val failure = assertThrows<StatusRuntimeException> { case.invoke(stub) }

        assertThat(failure.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(failure.status.description).isEqualTo(case.expectedMessage)
    }

    @Test
    fun `an unexpected failure is reported as INTERNAL`() {
        every { personService.get(1L) } throws IllegalStateException("database is on fire")

        val failure = assertThrows<StatusRuntimeException> {
            stub.getPerson(GetPersonRequest.newBuilder().setId(1L).build())
        }

        assertThat(failure.status.code).isEqualTo(Status.Code.INTERNAL)
        assertThat(failure.status.description).isEqualTo("database is on fire")
    }

    companion object {

        private const val PERSON_MISSING = "Person 404 not found"

        /**
         * Every handler that reads a record reaches the same shared status mapping, so the cases are
         * tabulated rather than duplicated. The two shapes that can be missing are a *person*
         * (adding a role for someone who was deleted) and a *role* row.
         */
        @JvmStatic
        fun notFoundCases(): List<RpcCase> = listOf(
            RpcCase(
                label = "getPerson",
                expectedMessage = PERSON_MISSING,
                arrange = { c -> every { c.people.get(404L) } throws NoSuchElementException(PERSON_MISSING) },
                invoke = { it.getPerson(GetPersonRequest.newBuilder().setId(404L).build()) },
            ),
            RpcCase(
                label = "updatePerson",
                expectedMessage = PERSON_MISSING,
                arrange = { c ->
                    every { c.people.update(404L, any(), any(), any()) } throws NoSuchElementException(PERSON_MISSING)
                },
                invoke = { it.updatePerson(UpdatePersonRequest.newBuilder().setId(404L).setName("Nobody").build()) },
            ),
            RpcCase(
                label = "deletePerson",
                expectedMessage = PERSON_MISSING,
                arrange = { c -> every { c.people.delete(404L) } throws NoSuchElementException(PERSON_MISSING) },
                invoke = { it.deletePerson(DeletePersonRequest.newBuilder().setId(404L).build()) },
            ),
            RpcCase(
                label = "addCastMember",
                expectedMessage = PERSON_MISSING,
                arrange = { c ->
                    every { c.cast.add(42L, 404L, any()) } throws NoSuchElementException(PERSON_MISSING)
                },
                invoke = {
                    it.addCastMember(
                        AddCastMemberRequest.newBuilder()
                            .setMovieId(42L).setPersonId(404L).setCharacterName("Nobody").build(),
                    )
                },
            ),
            RpcCase(
                label = "updateCastMember",
                expectedMessage = "Cast member 404 not found",
                arrange = { c ->
                    every { c.cast.updateCharacterName(404L, any()) } throws
                        NoSuchElementException("Cast member 404 not found")
                },
                invoke = {
                    it.updateCastMember(
                        UpdateCastMemberRequest.newBuilder()
                            .setId(404L).setCharacterName("X").build(),
                    )
                },
            ),
            RpcCase(
                label = "removeCastMember",
                expectedMessage = "Cast member 404 not found",
                arrange = { c ->
                    every { c.cast.remove(404L) } throws NoSuchElementException("Cast member 404 not found")
                },
                invoke = {
                    it.removeCastMember(
                        RemoveCastMemberRequest.newBuilder().setId(404L).build(),
                    )
                },
            ),
            RpcCase(
                label = "addCreator",
                expectedMessage = PERSON_MISSING,
                arrange = { c ->
                    every { c.creators.add(42L, 404L, any()) } throws NoSuchElementException(PERSON_MISSING)
                },
                invoke = {
                    it.addCreator(
                        AddCreatorRequest.newBuilder()
                            .setMovieId(42L).setPersonId(404L).setJob("Director").build(),
                    )
                },
            ),
            RpcCase(
                label = "updateCreator",
                expectedMessage = "Creator 404 not found",
                arrange = { c ->
                    every { c.creators.updateJob(404L, any()) } throws NoSuchElementException("Creator 404 not found")
                },
                invoke = { it.updateCreator(UpdateCreatorRequest.newBuilder().setId(404L).setJob("X").build()) },
            ),
            RpcCase(
                label = "removeCreator",
                expectedMessage = "Creator 404 not found",
                arrange = { c ->
                    every { c.creators.remove(404L) } throws NoSuchElementException("Creator 404 not found")
                },
                invoke = { it.removeCreator(RemoveCreatorRequest.newBuilder().setId(404L).build()) },
            ),
        )

        @JvmStatic
        fun invalidArgumentCases(): List<RpcCase> = listOf(
            RpcCase(
                label = "createPerson with an unparseable birth date",
                expectedMessage = "Invalid birth_date: 03-10-1967",
                // Fails while mapping the request, so no collaborator is arranged.
                arrange = { },
                invoke = {
                    it.createPerson(
                        CreatePersonRequest.newBuilder()
                            .setName("Denis Villeneuve").setBirthDate("03-10-1967").build(),
                    )
                },
            ),
            RpcCase(
                label = "updatePerson with an unparseable birth date",
                expectedMessage = "Invalid birth_date: 03-10-1967",
                arrange = { },
                invoke = {
                    it.updatePerson(
                        UpdatePersonRequest.newBuilder()
                            .setId(5L).setName("Denis Villeneuve").setBirthDate("03-10-1967").build(),
                    )
                },
            ),
            RpcCase(
                label = "a service rejecting the input",
                expectedMessage = "name is not allowed",
                arrange = { c ->
                    every { c.people.get(1L) } throws IllegalArgumentException("name is not allowed")
                },
                invoke = { it.getPerson(GetPersonRequest.newBuilder().setId(1L).build()) },
            ),
        )
    }
}
