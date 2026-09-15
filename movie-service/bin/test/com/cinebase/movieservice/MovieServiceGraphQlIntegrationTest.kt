package com.cinebase.movieservice

import com.cinebase.movieservice.artwork.ArtworkRepository
import com.cinebase.movieservice.artwork.ArtworkService
import com.cinebase.movieservice.artwork.ArtworkType
import com.cinebase.movieservice.config.UploadStorage
import com.cinebase.movieservice.grpc.PersonClient
import com.cinebase.movieservice.movie.Movie
import com.cinebase.movieservice.movie.MovieRepository
import com.cinebase.person.v1.CastMemberMessage
import com.cinebase.person.v1.CreatorMessage
import com.cinebase.person.v1.GetPeopleForMovieResponse
import com.cinebase.person.v1.PersonMessage
import com.cinebase.person.v1.SearchPeopleResponse
import io.grpc.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.GraphQlResponse
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.graphql.test.tester.entity
import org.springframework.graphql.test.tester.entityList
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The movie-service GraphQL API end to end: GraphQL → controllers → services → JPA (H2), with the
 * gRPC boundary replaced by a Mockito stub of [PersonClient].
 *
 * `PersonClient` is the only seam between movie-service and person-service, so stubbing it is what
 * proves the two rules that matter most here: people/cast/creator data always travels over gRPC
 * rather than through the movie database, and every gRPC status code is translated into the right
 * GraphQL error type.
 *
 * Upload *rejections* (empty / oversize / unsupported type) live in `ArtworkServiceTest`, because a
 * multipart part cannot be passed through `GraphQlTester`.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class MovieServiceGraphQlIntegrationTest {

    @Autowired
    private lateinit var tester: GraphQlTester

    @Autowired
    private lateinit var movies: MovieRepository

    @Autowired
    private lateinit var artworks: ArtworkRepository

    @Autowired
    private lateinit var artworkService: ArtworkService

    @Autowired
    private lateinit var storage: UploadStorage

    @MockitoBean
    private lateinit var personClient: PersonClient

    /** Unique per test, so rows left behind by other tests can never satisfy an assertion. */
    private val serial = AtomicInteger()

    // --- helpers ---

    private fun uniqueTitle(label: String) = "IT-$label-${serial.incrementAndGet()}"

    private fun storeMovie(title: String, releaseYear: Int? = null): Movie =
        movies.save(Movie(title = title, releaseYear = releaseYear))

    private fun person(
        id: Long,
        name: String,
        biography: String = "",
        birthDate: String = "",
    ): PersonMessage = PersonMessage.newBuilder()
        .setId(id)
        .setName(name)
        .setBiography(biography)
        .setBirthDate(birthDate)
        .setCreatedAt("2024-01-01T00:00:00Z")
        .setUpdatedAt("2024-01-01T00:00:00Z")
        .build()

    private fun only(response: GraphQlResponse) = response.errors.single()

    private fun image(name: String) = MockMultipartFile("file", name, "image/png", ByteArray(8) { 1 })

    private fun storedFile(url: String) = storage.uploadsDir.resolve(url.removePrefix("/uploads/"))

    // --- movies: create ---

    @Test
    fun `createMovie stores the movie and returns it`() {
        val title = uniqueTitle("Create")

        tester.document(CREATE_MOVIE)
            .variable(
                "input",
                mapOf(
                    "title" to title,
                    "synopsis" to "A trip through a wormhole.",
                    "releaseYear" to 2014,
                    "genre" to "Sci-Fi",
                ),
            )
            .execute()
            .path("createMovie.title")
            .entity<String>()
            .isEqualTo(title)

        val stored = movies.findAll().single { it.title == title }
        assertThat(stored.releaseYear).isEqualTo(2014)
        assertThat(stored.genre).isEqualTo("Sci-Fi")
    }

    @Test
    fun `createMovie rejects a blank title`() {
        val response = tester.document(CREATE_MOVIE)
            .variable("input", mapOf("title" to "   "))
            .execute()
            .returnResponse()

        val error = only(response)
        assertThat(error.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(error.message).isEqualTo("title must not be blank")
    }

    @ParameterizedTest(name = "releaseYear={0} is rejected")
    @CsvSource(
        "1887, releaseYear must be 1888 or later",
        "2101, releaseYear must be 2100 or earlier",
    )
    fun `createMovie rejects a release year outside the supported range`(releaseYear: Int, expectedMessage: String) {
        val response = tester.document(CREATE_MOVIE)
            .variable("input", mapOf("title" to uniqueTitle("Year"), "releaseYear" to releaseYear))
            .execute()
            .returnResponse()

        val error = only(response)
        assertThat(error.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(error.message).isEqualTo(expectedMessage)
    }

    // --- movies: read ---

    @Test
    fun `movie returns the stored movie`() {
        val stored = storeMovie(uniqueTitle("Read"), releaseYear = 2010)

        tester.document(FIND_MOVIE)
            .variable("id", stored.id.toString())
            .execute()
            .path("movie.title")
            .entity<String>()
            .isEqualTo(stored.title)
    }

    @Test
    fun `movie returns null for an unknown id instead of failing`() {
        tester.document(FIND_MOVIE)
            .variable("id", "987654")
            .execute()
            .path("movie")
            .valueIsNull()
    }

    // --- movies: update / delete ---

    @Test
    fun `updateMovie rewrites the stored movie`() {
        val stored = storeMovie(uniqueTitle("Update"))

        tester.document(UPDATE_MOVIE)
            .variable("id", stored.id.toString())
            .variable("input", mapOf("title" to stored.title, "synopsis" to "Rewritten.", "genre" to "Drama"))
            .execute()
            .path("updateMovie.synopsis")
            .entity<String>()
            .isEqualTo("Rewritten.")

        assertThat(movies.findById(stored.id).orElseThrow().genre).isEqualTo("Drama")
    }

    @Test
    fun `updateMovie reports NOT_FOUND for an unknown movie`() {
        val response = tester.document(UPDATE_MOVIE)
            .variable("id", "987654")
            .variable("input", mapOf("title" to "Ghost"))
            .execute()
            .returnResponse()

        val error = only(response)
        assertThat(error.errorType).isEqualTo(ErrorType.NOT_FOUND)
        assertThat(error.message).isEqualTo("Movie 987654 not found")
    }

    @Test
    fun `deleteMovie removes the movie and reports it as removed`() {
        val stored = storeMovie(uniqueTitle("Delete"))

        tester.document(DELETE_MOVIE)
            .variable("id", stored.id.toString())
            .execute()
            .path("deleteMovie")
            .entity<Boolean>()
            .isEqualTo(true)

        assertThat(movies.findById(stored.id)).isEmpty
    }

    @Test
    fun `deleteMovie reports NOT_FOUND for an unknown movie`() {
        val response = tester.document(DELETE_MOVIE)
            .variable("id", "987654")
            .execute()
            .returnResponse()

        assertThat(only(response).errorType).isEqualTo(ErrorType.NOT_FOUND)
    }

    // --- movies: listing ---

    @Test
    fun `movies matches titles case-insensitively`() {
        val title = storeMovie(uniqueTitle("CaseInsensitive")).title
        val needle = "caseinsensitive"

        val lower = tester.document(MOVIES)
            .variable("search", needle)
            .execute()
            .path("movies.items[*].title")
            .entityList<String>()
            .get()
        val upper = tester.document(MOVIES)
            .variable("search", needle.uppercase())
            .execute()
            .path("movies.items[*].title")
            .entityList<String>()
            .get()

        assertThat(lower).contains(title)
        assertThat(upper).isEqualTo(lower)
    }

    @Test
    fun `movies returns an empty page when nothing matches`() {
        tester.document(MOVIES)
            .variable("search", "zzzz-nothing-matches-this")
            .execute()
            .path("movies.total")
            .entity<Int>()
            .isEqualTo(0)
    }

    @Test
    fun `movies paginates a filtered result set and reports the total`() {
        val prefix = uniqueTitle("Paging")
        listOf("Alpha", "Bravo", "Charlie").forEach { storeMovie("$prefix $it") }

        val firstPage = tester.document(MOVIES)
            .variable("search", prefix)
            .variable("page", 0)
            .variable("size", 2)
            .execute()

        assertThat(firstPage.path("movies.total").entity<Int>().get()).isEqualTo(3)
        assertThat(firstPage.path("movies.totalPages").entity<Int>().get()).isEqualTo(2)
        assertThat(firstPage.path("movies.page").entity<Int>().get()).isEqualTo(0)
        assertThat(firstPage.path("movies.items[*].title").entityList<String>().get()).hasSize(2)

        val secondPage = tester.document(MOVIES)
            .variable("search", prefix)
            .variable("page", 1)
            .variable("size", 2)
            .execute()
            .path("movies.items[*].title")
            .entityList<String>()
            .get()

        assertThat(secondPage).hasSize(1)
    }

    @Test
    fun `movies sorts by title when asked`() {
        val prefix = uniqueTitle("Sorting")
        listOf("Charlie", "Alpha", "Bravo").forEach { storeMovie("$prefix $it") }

        val titles = tester.document(MOVIES)
            .variable("search", prefix)
            .variable("sort", mapOf("field" to "TITLE", "direction" to "ASC"))
            .execute()
            .path("movies.items[*].title")
            .entityList<String>()
            .get()

        assertThat(titles).containsExactly("$prefix Alpha", "$prefix Bravo", "$prefix Charlie")
    }

    @Test
    fun `movies clamps an oversized page size to the maximum`() {
        tester.document(MOVIES)
            .variable("size", 5_000)
            .execute()
            .path("movies.size")
            .entity<Int>()
            .isEqualTo(100)
    }

    // --- artwork ---

    @Test
    fun `artworks lists the movie's artwork in id order`() {
        val movie = storeMovie(uniqueTitle("Artwork"))
        artworkService.store(movie.id, ArtworkType.POSTER, image("a.png"))
        artworkService.store(movie.id, ArtworkType.BACKDROP, image("b.png"))

        val urls = tester.document(FIND_MOVIE)
            .variable("id", movie.id.toString())
            .execute()
            .path("movie.artworks[*].url")
            .entityList<String>()
            .get()

        assertThat(urls).hasSize(2)
        assertThat(urls[0]).endsWith(".png")
        // Storing the first artwork also made it the movie's cover.
        assertThat(movies.findById(movie.id).orElseThrow().artworkUrl).isEqualTo(urls[0])
    }

    @Test
    fun `removeMovieArtwork deletes the row, the file and promotes the next artwork to cover`() {
        val movie = storeMovie(uniqueTitle("RemoveArtwork"))
        val first = artworkService.store(movie.id, ArtworkType.POSTER, image("a.png"))
        val second = artworkService.store(movie.id, ArtworkType.BACKDROP, image("b.png"))
        val firstFile = storedFile(first.url)

        tester.document(REMOVE_ARTWORK)
            .variable("id", first.id.toString())
            .execute()
            .path("removeMovieArtwork")
            .entity<Boolean>()
            .isEqualTo(true)

        assertThat(artworks.findById(first.id)).isEmpty
        assertThat(firstFile).doesNotExist()
        assertThat(movies.findById(movie.id).orElseThrow().artworkUrl).isEqualTo(second.url)
    }

    @Test
    fun `removeMovieArtwork reports NOT_FOUND for an unknown artwork`() {
        val response = tester.document(REMOVE_ARTWORK)
            .variable("id", "987654")
            .execute()
            .returnResponse()

        val error = only(response)
        assertThat(error.errorType).isEqualTo(ErrorType.NOT_FOUND)
        assertThat(error.message).isEqualTo("Artwork 987654 not found")
    }

    // --- people over gRPC ---

    @Test
    fun `people maps the gRPC page onto the GraphQL page`() {
        Mockito.`when`(personClient.searchPeople(null, 0, 20)).thenReturn(
            SearchPeopleResponse.newBuilder()
                .addPeople(person(7, "Matthew McConaughey"))
                .addPeople(person(8, "Anne Hathaway"))
                .setTotal(2)
                .build(),
        )

        val response = tester.document(PEOPLE).execute()

        assertThat(response.path("people.total").entity<Int>().get()).isEqualTo(2)
        assertThat(response.path("people.totalPages").entity<Int>().get()).isEqualTo(1)
        assertThat(response.path("people.items[*].name").entityList<String>().get())
            .containsExactly("Matthew McConaughey", "Anne Hathaway")
    }

    @Test
    fun `people narrows the requested page through to person-service`() {
        Mockito.`when`(personClient.searchPeople("nolan", 2, 5))
            .thenReturn(SearchPeopleResponse.newBuilder().setTotal(0).build())

        assertThat(
            tester.document(PEOPLE)
                .variable("search", "nolan")
                .variable("page", 2)
                .variable("size", 5)
                .execute()
                .path("people.items[*].name")
                .entityList<String>()
                .get(),
        ).isEmpty()

        Mockito.verify(personClient).searchPeople("nolan", 2, 5)
    }

    @Test
    fun `createPerson forwards the input to person-service and maps the response`() {
        Mockito.`when`(personClient.createPerson("Denis Villeneuve", "Canadian director", "1967-10-03"))
            .thenReturn(person(1, "Denis Villeneuve", "Canadian director", "1967-10-03"))

        val response = tester.document(CREATE_PERSON)
            .variable(
                "input",
                mapOf("name" to "Denis Villeneuve", "biography" to "Canadian director", "birthDate" to "1967-10-03"),
            )
            .execute()

        assertThat(response.path("createPerson.name").entity<String>().get()).isEqualTo("Denis Villeneuve")
        assertThat(response.path("createPerson.birthDate").entity<String>().get()).isEqualTo("1967-10-03")
    }

    @Test
    fun `createPerson accepts a person without a birth date`() {
        Mockito.`when`(personClient.createPerson("Anonymous", null as String?, null as String?))
            .thenReturn(person(2, "Anonymous"))

        tester.document(CREATE_PERSON)
            .variable("input", mapOf("name" to "Anonymous"))
            .execute()
            .path("createPerson.name")
            .entity<String>()
            .isEqualTo("Anonymous")
    }

    @Test
    fun `addCastMember returns the created role together with its person`() {
        val movie = storeMovie(uniqueTitle("AddCast"))
        Mockito.`when`(personClient.addCastMember(movie.id, 7L, "Cooper")).thenReturn(
            CastMemberMessage.newBuilder()
                .setId(1)
                .setMovieId(movie.id)
                .setPerson(person(7, "Matthew McConaughey"))
                .setCharacterName("Cooper")
                .build(),
        )

        val response = tester.document(ADD_CAST)
            .variable("input", mapOf("movieId" to movie.id.toString(), "personId" to "7", "characterName" to "Cooper"))
            .execute()

        assertThat(response.path("addCastMember.characterName").entity<String>().get()).isEqualTo("Cooper")
        assertThat(response.path("addCastMember.person.name").entity<String>().get()).isEqualTo("Matthew McConaughey")
    }

    @Test
    fun `addCastMember rejects a blank character name without touching person-service`() {
        val response = tester.document(ADD_CAST)
            .variable("input", mapOf("movieId" to "1", "personId" to "7", "characterName" to "  "))
            .execute()
            .returnResponse()

        assertThat(only(response).errorType).isEqualTo(ErrorType.BAD_REQUEST)
        Mockito.verifyNoInteractions(personClient)
    }

    @Test
    fun `addCreator returns the created job together with its person`() {
        val movie = storeMovie(uniqueTitle("AddCreator"))
        Mockito.`when`(personClient.addCreator(movie.id, 8L, "Director")).thenReturn(
            CreatorMessage.newBuilder()
                .setId(2)
                .setMovieId(movie.id)
                .setPerson(person(8, "Christopher Nolan"))
                .setJob("Director")
                .build(),
        )

        val response = tester.document(ADD_CREATOR)
            .variable("input", mapOf("movieId" to movie.id.toString(), "personId" to "8", "job" to "Director"))
            .execute()

        assertThat(response.path("addCreator.job").entity<String>().get()).isEqualTo("Director")
        assertThat(response.path("addCreator.person.name").entity<String>().get()).isEqualTo("Christopher Nolan")
    }

    @Test
    fun `a movie's cast and creators are resolved from person-service over gRPC`() {
        val movie = storeMovie(uniqueTitle("Roles"))
        Mockito.`when`(personClient.getPeopleForMovie(movie.id)).thenReturn(
            GetPeopleForMovieResponse.newBuilder()
                .addCast(
                    CastMemberMessage.newBuilder()
                        .setId(1)
                        .setMovieId(movie.id)
                        .setPerson(person(7, "Matthew McConaughey"))
                        .setCharacterName("Cooper")
                        .build(),
                )
                .addCreators(
                    CreatorMessage.newBuilder()
                        .setId(2)
                        .setMovieId(movie.id)
                        .setPerson(person(8, "Christopher Nolan"))
                        .setJob("Director")
                        .build(),
                )
                .build(),
        )

        val response = tester.document(FIND_MOVIE_WITH_ROLES)
            .variable("id", movie.id.toString())
            .execute()

        assertThat(response.path("movie.cast[*].characterName").entityList<String>().get()).containsExactly("Cooper")
        assertThat(response.path("movie.cast[*].person.name").entityList<String>().get())
            .containsExactly("Matthew McConaughey")
        assertThat(response.path("movie.creators[*].job").entityList<String>().get()).containsExactly("Director")
    }

    @Test
    fun `a movie without cast or creators renders empty lists`() {
        val movie = storeMovie(uniqueTitle("NoRoles"))
        Mockito.`when`(personClient.getPeopleForMovie(movie.id))
            .thenReturn(GetPeopleForMovieResponse.getDefaultInstance())

        val response = tester.document(FIND_MOVIE_WITH_ROLES).variable("id", movie.id.toString()).execute()

        assertThat(response.path("movie.cast[*].id").entityList<String>().get()).isEmpty()
        assertThat(response.path("movie.creators[*].id").entityList<String>().get()).isEmpty()
    }

    // --- gRPC failure mapping ---

    @Test
    fun `a missing person is surfaced as a NOT_FOUND GraphQL error`() {
        Mockito.`when`(personClient.searchPeople(null, 0, 20))
            .thenThrow(Status.NOT_FOUND.withDescription("Person 404 not found").asRuntimeException())

        val response = tester.document(PEOPLE).execute().returnResponse()

        val error = only(response)
        assertThat(error.errorType).isEqualTo(ErrorType.NOT_FOUND)
        assertThat(error.message).isEqualTo("Person 404 not found")
    }

    @Test
    fun `an invalid person request is surfaced as a BAD_REQUEST GraphQL error`() {
        Mockito.`when`(personClient.searchPeople(null, 0, 20))
            .thenThrow(Status.INVALID_ARGUMENT.withDescription("Invalid query").asRuntimeException())

        assertThat(only(tester.document(PEOPLE).execute().returnResponse()).errorType)
            .isEqualTo(ErrorType.BAD_REQUEST)
    }

    @Test
    fun `an unexpected gRPC failure is surfaced as an INTERNAL_ERROR GraphQL error`() {
        Mockito.`when`(personClient.searchPeople(null, 0, 20))
            .thenThrow(Status.INTERNAL.withDescription("person-service is unreachable").asRuntimeException())

        val error = only(tester.document(PEOPLE).execute().returnResponse())

        assertThat(error.errorType).isEqualTo(ErrorType.INTERNAL_ERROR)
        assertThat(error.message).isEqualTo("person-service is unreachable")
    }

    companion object {

        private val CREATE_MOVIE = """
            mutation CreateMovie(${'$'}input: MovieInput!) {
              createMovie(input: ${'$'}input) { id title synopsis releaseYear genre }
            }
        """.trimIndent()

        private val UPDATE_MOVIE = """
            mutation UpdateMovie(${'$'}id: ID!, ${'$'}input: MovieInput!) {
              updateMovie(id: ${'$'}id, input: ${'$'}input) { id title synopsis genre }
            }
        """.trimIndent()

        private val DELETE_MOVIE = """
            mutation DeleteMovie(${'$'}id: ID!) { deleteMovie(id: ${'$'}id) }
        """.trimIndent()

        private val FIND_MOVIE = """
            query Movie(${'$'}id: ID!) {
              movie(id: ${'$'}id) { id title artworkUrl artworks { id url type } }
            }
        """.trimIndent()

        private val FIND_MOVIE_WITH_ROLES = """
            query MovieWithRoles(${'$'}id: ID!) {
              movie(id: ${'$'}id) {
                id
                cast { id characterName person { id name } }
                creators { id job person { id name } }
              }
            }
        """.trimIndent()

        private val MOVIES = """
            query Movies(${'$'}search: String, ${'$'}sort: MovieSort, ${'$'}page: Int, ${'$'}size: Int) {
              movies(search: ${'$'}search, sort: ${'$'}sort, page: ${'$'}page, size: ${'$'}size) {
                items { id title releaseYear }
                total
                page
                size
                totalPages
              }
            }
        """.trimIndent()

        private val REMOVE_ARTWORK = """
            mutation RemoveArtwork(${'$'}id: ID!) { removeMovieArtwork(id: ${'$'}id) }
        """.trimIndent()

        private val PEOPLE = """
            query People(${'$'}search: String, ${'$'}page: Int, ${'$'}size: Int) {
              people(search: ${'$'}search, page: ${'$'}page, size: ${'$'}size) {
                items { id name biography birthDate }
                total
                page
                size
                totalPages
              }
            }
        """.trimIndent()

        private val CREATE_PERSON = """
            mutation CreatePerson(${'$'}input: PersonInput!) {
              createPerson(input: ${'$'}input) { id name biography birthDate }
            }
        """.trimIndent()

        private val ADD_CAST = """
            mutation AddCast(${'$'}input: CastInput!) {
              addCastMember(input: ${'$'}input) { id movieId characterName person { id name } }
            }
        """.trimIndent()

        private val ADD_CREATOR = """
            mutation AddCreator(${'$'}input: CreatorInput!) {
              addCreator(input: ${'$'}input) { id movieId job person { id name } }
            }
        """.trimIndent()
    }
}
