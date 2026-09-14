package com.cinebase.movieservice.graphql

import com.cinebase.movieservice.grpc.PersonClient
import jakarta.validation.Valid
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

/**
 * People, cast and creator GraphQL operations.
 *
 * Every method here delegates to person-service over gRPC — movie-service never touches the
 * person database directly. gRPC failures surface as GraphQL errors via [GraphQlExceptionAdvice].
 */
@Controller
class PeopleController(private val people: PersonClient) {

    // --- Queries ---

    @QueryMapping
    fun people(
        @Argument search: String?,
        @Argument page: Int?,
        @Argument size: Int?,
    ): PersonPageDto {
        val pageNumber = (page ?: 0).coerceAtLeast(0)
        val pageSize = (size ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val response = people.searchPeople(search, pageNumber, pageSize)
        val total = response.total.toInt()
        return PersonPageDto(
            items = response.peopleList.map { it.toDto() },
            total = total,
            page = pageNumber,
            size = pageSize,
            totalPages = (total + pageSize - 1) / pageSize,
        )
    }

    // --- Person mutations ---

    @MutationMapping
    fun createPerson(@Argument @Valid input: PersonInput): PersonDto =
        people.createPerson(input.name, input.biography, input.birthDate?.toString()).toDto()

    @MutationMapping
    fun updatePerson(@Argument id: Long, @Argument @Valid input: PersonInput): PersonDto =
        people.updatePerson(id, input.name, input.biography, input.birthDate?.toString()).toDto()

    @MutationMapping
    fun deletePerson(@Argument id: Long): Boolean = people.deletePerson(id)

    // --- Cast mutations ---

    @MutationMapping
    fun addCastMember(@Argument @Valid input: CastInput): CastMemberDto =
        people.addCastMember(input.movieId, input.personId, input.characterName).toDto()

    @MutationMapping
    fun updateCastMember(@Argument id: Long, @Argument @Valid input: CastInput): CastMemberDto =
        people.updateCastMember(id, input.characterName).toDto()

    @MutationMapping
    fun removeCastMember(@Argument id: Long): Boolean = people.removeCastMember(id)

    // --- Creator mutations ---

    @MutationMapping
    fun addCreator(@Argument @Valid input: CreatorInput): CreatorDto =
        people.addCreator(input.movieId, input.personId, input.job).toDto()

    @MutationMapping
    fun updateCreator(@Argument id: Long, @Argument @Valid input: CreatorInput): CreatorDto =
        people.updateCreator(id, input.job).toDto()

    @MutationMapping
    fun removeCreator(@Argument id: Long): Boolean = people.removeCreator(id)
}
