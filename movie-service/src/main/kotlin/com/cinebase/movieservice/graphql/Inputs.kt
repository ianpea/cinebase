package com.cinebase.movieservice.graphql

import com.cinebase.movieservice.movie.Movie
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Sort
import java.time.LocalDate

/**
 * GraphQL input objects and result envelopes.
 *
 * Spring GraphQL binds input objects through the primary data constructor, so these are plain
 * Kotlin classes whose property names match the SDL fields. Bean Validation annotations are
 * enforced because the controller parameters are declared with `@Valid`.
 */

const val DEFAULT_PAGE_SIZE = 20
const val MAX_PAGE_SIZE = 100
const val SEARCH_LIMIT = 20

class MovieInput(
    @field:NotBlank(message = "title must not be blank")
    @field:Size(max = 255, message = "title must be at most 255 characters")
    val title: String = "",

    val synopsis: String? = null,

    @field:Min(value = 1888, message = "releaseYear must be 1888 or later")
    @field:Max(value = 2100, message = "releaseYear must be 2100 or earlier")
    val releaseYear: Int? = null,

    @field:Size(max = 120, message = "genre must be at most 120 characters")
    val genre: String? = null,
)

class PersonInput(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 255, message = "name must be at most 255 characters")
    val name: String = "",

    val biography: String? = null,

    val birthDate: LocalDate? = null,
)

class CastInput(
    val movieId: Long = 0,
    val personId: Long = 0,

    @field:NotBlank(message = "characterName must not be blank")
    val characterName: String = "",
)

class CreatorInput(
    val movieId: Long = 0,
    val personId: Long = 0,

    @field:NotBlank(message = "job must not be blank")
    val job: String = "",
)

class MovieSortInput(
    val field: MovieSortField = MovieSortField.CREATED_AT,
    val direction: SortDirection = SortDirection.DESC,
)

enum class MovieSortField {
    TITLE,
    RELEASE_YEAR,
    CREATED_AT,
}

enum class SortDirection {
    ASC,
    DESC,
}

/** Translates the GraphQL sort input into a Spring Data [Sort] on movie entity properties. */
fun MovieSortInput.toSort(): Sort = Sort.by(
    when (direction) {
        SortDirection.ASC -> Sort.Direction.ASC
        SortDirection.DESC -> Sort.Direction.DESC
    },
    when (field) {
        MovieSortField.TITLE -> "title"
        MovieSortField.RELEASE_YEAR -> "releaseYear"
        MovieSortField.CREATED_AT -> "createdAt"
    },
)

data class MoviePageDto(
    val items: List<Movie>,
    val total: Int,
    val page: Int,
    val size: Int,
    val totalPages: Int,
)

data class PersonPageDto(
    val items: List<PersonDto>,
    val total: Int,
    val page: Int,
    val size: Int,
    val totalPages: Int,
)

data class SearchResultDto(
    val movies: List<Movie>,
    val people: List<PersonDto>,
)
