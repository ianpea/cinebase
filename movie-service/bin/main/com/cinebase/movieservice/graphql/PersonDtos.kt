package com.cinebase.movieservice.graphql

import com.cinebase.person.v1.CastMemberMessage
import com.cinebase.person.v1.CreatorMessage
import com.cinebase.person.v1.PersonMessage
import java.time.Instant
import java.time.LocalDate

/**
 * GraphQL DTOs for person-service data resolved over gRPC.
 *
 * The `Movie` JPA entity already maps 1:1 to the GraphQL `Movie` type, but cast/creator/person
 * data lives in person-service, so we shape the gRPC messages into these records that match the
 * GraphQL schema (`Person`, `MovieCast`, `MovieCreator`).
 */
data class PersonDto(
    val id: Long,
    val name: String,
    val biography: String?,
    val birthDate: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CastMemberDto(
    val id: Long,
    val movieId: Long,
    val person: PersonDto,
    val characterName: String,
)

data class CreatorDto(
    val id: Long,
    val movieId: Long,
    val person: PersonDto,
    val job: String,
)

fun PersonMessage.toDto(): PersonDto = PersonDto(
    id = id,
    name = name,
    biography = biography.takeUnless { it.isBlank() },
    birthDate = birthDate.takeUnless { it.isBlank() }?.let(LocalDate::parse),
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
)

fun CastMemberMessage.toDto(): CastMemberDto = CastMemberDto(
    id = id,
    movieId = movieId,
    person = person.toDto(),
    characterName = characterName,
)

fun CreatorMessage.toDto(): CreatorDto = CreatorDto(
    id = id,
    movieId = movieId,
    person = person.toDto(),
    job = job,
)
