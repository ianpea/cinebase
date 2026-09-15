package com.cinebase.personservice

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
import com.cinebase.personservice.cast.MovieCastRepository
import com.cinebase.personservice.creator.MovieCreatorRepository
import com.cinebase.personservice.person.PersonService
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * End-to-end person-service test: a real gRPC client → real gRPC handlers → real Spring Data JPA
 * repositories → H2. Nothing is mocked, so request mapping, transactions, persistence and the proto
 * response mapping are all verified together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PersonServiceGrpcIntegrationTest @Autowired constructor(
    private val lifecycle: GrpcServerLifecycle,
    private val people: PersonService,
    private val cast: MovieCastRepository,
    private val creators: MovieCreatorRepository,
) {

    private lateinit var channel: ManagedChannel
    private lateinit var stub: PersonServiceGrpc.PersonServiceBlockingStub

    /** Each test works on its own movie so roles from other tests cannot leak in. */
    private fun nextMovieId(): Long = movies.incrementAndGet()

    private fun castRole(movieId: Long, personId: Long, characterName: String) =
        stub.addCastMember(
            AddCastMemberRequest.newBuilder()
                .setMovieId(movieId)
                .setPersonId(personId)
                .setCharacterName(characterName)
                .build(),
        )

    private fun creatorRole(movieId: Long, personId: Long, job: String) =
        stub.addCreator(
            AddCreatorRequest.newBuilder().setMovieId(movieId).setPersonId(personId).setJob(job).build(),
        )

    @BeforeEach
    fun connect() {
        channel = ManagedChannelBuilder.forAddress("localhost", lifecycle.port).usePlaintext().build()
        stub = PersonServiceGrpc.newBlockingStub(channel)
    }

    @AfterEach
    fun disconnect() {
        channel.shutdownNow()
    }

    // --- people: create / read ---

    @Test
    fun `createPerson round-trips every field`() {
        val created = stub.createPerson(
            CreatePersonRequest.newBuilder()
                .setName("  Denis Villeneuve  ")
                .setBiography("  Canadian director  ")
                .setBirthDate("1967-10-03")
                .build(),
        )

        assertThat(created.id).isPositive()
        assertThat(created.name).isEqualTo("Denis Villeneuve")
        assertThat(created.biography).isEqualTo("Canadian director")
        assertThat(created.birthDate).isEqualTo("1967-10-03")
        assertThat(created.createdAt).isNotBlank()
        assertThat(created.updatedAt).isNotBlank()
    }

    @Test
    fun `createPerson accepts a person with only a name`() {
        val created = stub.createPerson(CreatePersonRequest.newBuilder().setName("Anonymous").build())

        assertThat(created.name).isEqualTo("Anonymous")
        assertThat(created.biography).isEmpty()
        assertThat(created.birthDate).isEmpty()
    }

    @Test
    fun `createPerson rejects an unparseable birth date`() {
        val failure = assertThrows<StatusRuntimeException> {
            stub.createPerson(
                CreatePersonRequest.newBuilder().setName("Denis Villeneuve").setBirthDate("03-10-1967").build(),
            )
        }

        assertThat(failure.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(failure.status.description).contains("Invalid birth_date: 03-10-1967")
    }

    @Test
    fun `createPerson rejects a person whose lowercased name and birth date already exist`() {
        people.create("Ava DuVernay", null, LocalDate.of(1972, 8, 24))

        val failure = assertThrows<StatusRuntimeException> {
            stub.createPerson(
                CreatePersonRequest.newBuilder()
                    .setName("  AVA DUVERNAY  ")
                    .setBirthDate("1972-08-24")
                    .build(),
            )
        }

        assertThat(failure.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(failure.status.description)
            .isEqualTo("A person named 'AVA DUVERNAY' born 1972-08-24 already exists")
    }

    @Test
    fun `createPerson treats two people without a birth date as the same person`() {
        people.create("Second Unit", null, null)

        val failure = assertThrows<StatusRuntimeException> {
            stub.createPerson(CreatePersonRequest.newBuilder().setName("SECOND UNIT").build())
        }

        assertThat(failure.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(failure.status.description)
            .isEqualTo("A person named 'SECOND UNIT' with no birth date already exists")
    }

    @Test
    fun `createPerson allows the same name with a different birth date`() {
        people.create("Bong Joon-ho", null, LocalDate.of(1969, 9, 14))

        val created = stub.createPerson(
            CreatePersonRequest.newBuilder().setName("Bong Joon-ho").setBirthDate("1969-09-15").build(),
        )

        assertThat(created.id).isPositive()
        assertThat(created.birthDate).isEqualTo("1969-09-15")
    }

    @Test
    fun `getPerson returns a stored person`() {
        val created = people.create("Greta Gerwig", "British director", LocalDate.of(1983, 8, 4))

        val fetched = stub.getPerson(GetPersonRequest.newBuilder().setId(created.id).build())

        assertThat(fetched.name).isEqualTo("Greta Gerwig")
        assertThat(fetched.birthDate).isEqualTo("1983-08-04")
    }

    @Test
    fun `getPerson reports NOT_FOUND for an unknown id`() {
        val failure = assertThrows<StatusRuntimeException> {
            stub.getPerson(GetPersonRequest.newBuilder().setId(987_654L).build())
        }

        assertThat(failure.status.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(failure.status.description).isEqualTo("Person 987654 not found")
    }

    // --- people: search ---

    @Test
    fun `searchPeople matches names case-insensitively`() {
        people.create("Quentin Tarantino", null, null)

        val lower = stub.searchPeople(SearchPeopleRequest.newBuilder().setQuery("tarantino").build())
        val upper = stub.searchPeople(SearchPeopleRequest.newBuilder().setQuery("TARANTINO").build())

        assertThat(lower.peopleList.map { it.name }).contains("Quentin Tarantino")
        assertThat(upper.peopleList.map { it.name }).isEqualTo(lower.peopleList.map { it.name })
    }

    @Test
    fun `searchPeople returns an empty page when nothing matches`() {
        val response = stub.searchPeople(
            SearchPeopleRequest.newBuilder().setQuery("zzzz-nobody").setPage(0).setSize(10).build(),
        )

        assertThat(response.peopleList).isEmpty()
        assertThat(response.total).isEqualTo(0)
    }

    @Test
    fun `searchPeople returns every person when the query is blank`() {
        val response = stub.searchPeople(SearchPeopleRequest.newBuilder().setQuery("").build())

        assertThat(response.total).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `searchPeople clamps the requested page size`() {
        // size 0 is clamped to 1, so the first page holds exactly one person.
        val response = stub.searchPeople(
            SearchPeopleRequest.newBuilder().setQuery("").setPage(0).setSize(0).build(),
        )

        assertThat(response.peopleList).hasSize(1)
    }

    // --- people: update / delete ---

    @Test
    fun `updatePerson rewrites the stored person`() {
        val created = people.create("Chris Nolan", null, null)

        val updated = stub.updatePerson(
            UpdatePersonRequest.newBuilder()
                .setId(created.id)
                .setName("Christopher Nolan")
                .setBiography("Director")
                .setBirthDate("1970-07-30")
                .build(),
        )

        assertThat(updated.name).isEqualTo("Christopher Nolan")
        assertThat(updated.biography).isEqualTo("Director")
        assertThat(updated.birthDate).isEqualTo("1970-07-30")
    }

    @Test
    fun `updatePerson clears optional fields when they are omitted`() {
        val created = people.create("Patty Jenkins", "Director", LocalDate.of(1971, 7, 24))

        val updated = stub.updatePerson(
            UpdatePersonRequest.newBuilder().setId(created.id).setName("Patty Jenkins").build(),
        )

        assertThat(updated.biography).isEmpty()
        assertThat(updated.birthDate).isEmpty()
    }

    @Test
    fun `updatePerson rejects renaming onto an existing person`() {
        people.create("Kathryn Bigelow", "Director", LocalDate.of(1951, 11, 27))
        val other = people.create("Sofia Coppola", null, null)

        val failure = assertThrows<StatusRuntimeException> {
            stub.updatePerson(
                UpdatePersonRequest.newBuilder()
                    .setId(other.id)
                    .setName("kathryn bigelow")
                    .setBirthDate("1951-11-27")
                    .build(),
            )
        }

        assertThat(failure.status.code).isEqualTo(Status.Code.INVALID_ARGUMENT)
        assertThat(failure.status.description).contains("already exists")
    }

    @Test
    fun `updatePerson keeps its own name and birth date`() {
        val created = people.create("Wes Anderson", "Director", LocalDate.of(1969, 5, 1))

        val updated = stub.updatePerson(
            UpdatePersonRequest.newBuilder()
                .setId(created.id)
                .setName("Wes Anderson")
                .setBirthDate("1969-05-01")
                .setBiography("Writer")
                .build(),
        )

        assertThat(updated.biography).isEqualTo("Writer")
    }

    @Test
    fun `deletePerson removes the person`() {
        val created = people.create("Temporary", null, null)

        val response = stub.deletePerson(DeletePersonRequest.newBuilder().setId(created.id).build())

        assertThat(response.deleted).isTrue()
        assertThat(people.findAll(listOf(created.id))).isEmpty()
    }

    // --- cast ---

    @Test
    fun `addCastMember stores the role and returns it with its person`() {
        val movieId = nextMovieId()
        val actor = people.create("Matthew McConaughey", "Actor", null)

        val added = stub.addCastMember(
            AddCastMemberRequest.newBuilder()
                .setMovieId(movieId)
                .setPersonId(actor.id)
                .setCharacterName("  Cooper  ")
                .build(),
        )

        assertThat(added.movieId).isEqualTo(movieId)
        assertThat(added.characterName).isEqualTo("Cooper")
        assertThat(added.person.id).isEqualTo(actor.id)
        assertThat(added.person.name).isEqualTo("Matthew McConaughey")
    }

    @Test
    fun `addCastMember reports NOT_FOUND for a missing person`() {
        val failure = assertThrows<StatusRuntimeException> {
            stub.addCastMember(
                AddCastMemberRequest.newBuilder()
                    .setMovieId(nextMovieId())
                    .setPersonId(987_654L)
                    .setCharacterName("Ghost")
                    .build(),
            )
        }

        assertThat(failure.status.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(failure.status.description).isEqualTo("Person 987654 not found")
    }

    @Test
    fun `addCastMember allows the same person to hold two roles in one movie`() {
        val movieId = nextMovieId()
        val actor = people.create("Michael J. Fox", null, null)

        val young = stub.addCastMember(
            AddCastMemberRequest.newBuilder().setMovieId(movieId).setPersonId(actor.id)
                .setCharacterName("Marty").build(),
        )
        val old = stub.addCastMember(
            AddCastMemberRequest.newBuilder().setMovieId(movieId).setPersonId(actor.id)
                .setCharacterName("Old Marty").build(),
        )

        assertThat(young.id).isNotEqualTo(old.id)
        val roles = stub.getPeopleForMovie(GetPeopleForMovieRequest.newBuilder().setMovieId(movieId).build())
        assertThat(roles.castList.map { it.characterName }).containsExactly("Marty", "Old Marty")
    }

    @Test
    fun `updateCastMember rewrites the character name`() {
        val movieId = nextMovieId()
        val actor = people.create("Emma Stone", null, null)
        val added = stub.addCastMember(
            AddCastMemberRequest.newBuilder().setMovieId(movieId).setPersonId(actor.id)
                .setCharacterName("Coop").build(),
        )

        val updated = stub.updateCastMember(
            UpdateCastMemberRequest.newBuilder().setId(added.id).setCharacterName("Joseph Cooper").build(),
        )

        assertThat(updated.characterName).isEqualTo("Joseph Cooper")
        assertThat(updated.person.name).isEqualTo("Emma Stone")
    }

    @Test
    fun `updateCastMember reports NOT_FOUND for a missing role`() {
        val failure = assertThrows<StatusRuntimeException> {
            stub.updateCastMember(
                UpdateCastMemberRequest.newBuilder().setId(987_654L).setCharacterName("X").build(),
            )
        }

        assertThat(failure.status.code).isEqualTo(Status.Code.NOT_FOUND)
    }

    @Test
    fun `removeCastMember deletes the role`() {
        val movieId = nextMovieId()
        val actor = people.create("Anne Hathaway", null, null)
        val added = stub.addCastMember(
            AddCastMemberRequest.newBuilder().setMovieId(movieId).setPersonId(actor.id)
                .setCharacterName("Brand").build(),
        )

        val response = stub.removeCastMember(RemoveCastMemberRequest.newBuilder().setId(added.id).build())

        assertThat(response.removed).isTrue()
        val roles = stub.getPeopleForMovie(GetPeopleForMovieRequest.newBuilder().setMovieId(movieId).build())
        assertThat(roles.castList).isEmpty()
    }

    // --- creators ---

    @Test
    fun `addCreator stores the role and returns it with its person`() {
        val movieId = nextMovieId()
        val director = people.create("Jordan Peele", null, null)

        val added = stub.addCreator(
            AddCreatorRequest.newBuilder().setMovieId(movieId).setPersonId(director.id)
                .setJob("  Director  ").build(),
        )

        assertThat(added.movieId).isEqualTo(movieId)
        assertThat(added.job).isEqualTo("Director")
        assertThat(added.person.name).isEqualTo("Jordan Peele")
    }

    @Test
    fun `updateCreator rewrites the job`() {
        val movieId = nextMovieId()
        val person = people.create("Steven Spielberg", null, null)
        val added = stub.addCreator(
            AddCreatorRequest.newBuilder().setMovieId(movieId).setPersonId(person.id).setJob("Director").build(),
        )

        val updated = stub.updateCreator(
            UpdateCreatorRequest.newBuilder().setId(added.id).setJob("Executive Producer").build(),
        )

        assertThat(updated.job).isEqualTo("Executive Producer")
        assertThat(updated.person.name).isEqualTo("Steven Spielberg")
    }

    @Test
    fun `removeCreator deletes the role`() {
        val movieId = nextMovieId()
        val person = people.create("Denis Villeneuve", null, null)
        val added = stub.addCreator(
            AddCreatorRequest.newBuilder().setMovieId(movieId).setPersonId(person.id).setJob("Director").build(),
        )

        val response = stub.removeCreator(RemoveCreatorRequest.newBuilder().setId(added.id).build())

        assertThat(response.removed).isTrue()
        val roles = stub.getPeopleForMovie(GetPeopleForMovieRequest.newBuilder().setMovieId(movieId).build())
        assertThat(roles.creatorsList).isEmpty()
    }

    // --- movie detail read path ---

    @Test
    fun `getPeopleForMovie returns cast and creators together`() {
        val movieId = nextMovieId()
        val director = people.create("Ridley Scott", "Director", null)
        val actor = people.create("Harrison Ford", "Actor", null)
        stub.addCastMember(
            AddCastMemberRequest.newBuilder().setMovieId(movieId).setPersonId(actor.id)
                .setCharacterName("Deckard").build(),
        )
        stub.addCreator(
            AddCreatorRequest.newBuilder().setMovieId(movieId).setPersonId(director.id).setJob("Director").build(),
        )

        val response = stub.getPeopleForMovie(GetPeopleForMovieRequest.newBuilder().setMovieId(movieId).build())

        assertThat(response.castCount).isEqualTo(1)
        assertThat(response.getCast(0).characterName).isEqualTo("Deckard")
        assertThat(response.getCast(0).person.name).isEqualTo("Harrison Ford")

        assertThat(response.creatorsCount).isEqualTo(1)
        assertThat(response.getCreators(0).job).isEqualTo("Director")
        assertThat(response.getCreators(0).person.name).isEqualTo("Ridley Scott")
    }

    @Test
    fun `getPeopleForMovie returns empty lists for a movie with no roles`() {
        val response = stub.getPeopleForMovie(
            GetPeopleForMovieRequest.newBuilder().setMovieId(nextMovieId()).build(),
        )

        assertThat(response.castList).isEmpty()
        assertThat(response.creatorsList).isEmpty()
    }

    @Test
    fun `deletePerson removes the person together with every role that points at them`() {
        val sharedMovie = nextMovieId()
        val otherMovie = nextMovieId()
        val leaving = people.create("Leaving Person", null, null)
        val staying = people.create("Staying Person", null, null)

        castRole(sharedMovie, leaving.id, "Ghost")
        creatorRole(sharedMovie, leaving.id, "Director")
        castRole(otherMovie, leaving.id, "Hologram")
        creatorRole(otherMovie, leaving.id, "Producer")
        castRole(sharedMovie, staying.id, "Survivor")
        creatorRole(sharedMovie, staying.id, "Composer")

        val response = stub.deletePerson(DeletePersonRequest.newBuilder().setId(leaving.id).build())

        assertThat(response.deleted).isTrue()

        assertThat(cast.findByMovieIdOrderById(otherMovie)).isEmpty()
        assertThat(creators.findByMovieIdOrderById(otherMovie)).isEmpty()

        assertThat(cast.findByMovieIdOrderById(sharedMovie).map { it.personId }).containsExactly(staying.id)
        assertThat(cast.findByMovieIdOrderById(sharedMovie).map { it.characterName }).containsExactly("Survivor")
        assertThat(creators.findByMovieIdOrderById(sharedMovie).map { it.personId }).containsExactly(staying.id)
        assertThat(creators.findByMovieIdOrderById(sharedMovie).map { it.job }).containsExactly("Composer")

        assertThat(people.findAll(listOf(leaving.id))).isEmpty()
        val movie = stub.getPeopleForMovie(GetPeopleForMovieRequest.newBuilder().setMovieId(sharedMovie).build())
        assertThat(movie.castList.map { it.person.name }).containsExactly("Staying Person")
        assertThat(movie.creatorsList.map { it.person.name }).containsExactly("Staying Person")
    }

    @Test
    fun `roles are kept per movie`() {
        val first = nextMovieId()
        val second = nextMovieId()
        val actor = people.create("Tilda Swinton", null, null)
        stub.addCastMember(
            AddCastMemberRequest.newBuilder().setMovieId(first).setPersonId(actor.id)
                .setCharacterName("First").build(),
        )
        stub.addCastMember(
            AddCastMemberRequest.newBuilder().setMovieId(second).setPersonId(actor.id)
                .setCharacterName("Second").build(),
        )

        val firstRoles = stub.getPeopleForMovie(GetPeopleForMovieRequest.newBuilder().setMovieId(first).build())
        val secondRoles = stub.getPeopleForMovie(GetPeopleForMovieRequest.newBuilder().setMovieId(second).build())

        assertThat(firstRoles.castList.map { it.characterName }).containsExactly("First")
        assertThat(secondRoles.castList.map { it.characterName }).containsExactly("Second")
    }

    companion object {
        private val movies = AtomicLong(1_000)
    }
}
