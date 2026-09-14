package com.cinebase.personservice.grpc

import com.cinebase.person.v1.AddCastMemberRequest
import com.cinebase.person.v1.AddCreatorRequest
import com.cinebase.person.v1.CastMemberMessage
import com.cinebase.person.v1.CreatePersonRequest
import com.cinebase.person.v1.CreatorMessage
import com.cinebase.person.v1.DeletePersonRequest
import com.cinebase.person.v1.DeletePersonResponse
import com.cinebase.person.v1.GetPeopleForMovieRequest
import com.cinebase.person.v1.GetPeopleForMovieResponse
import com.cinebase.person.v1.GetPersonRequest
import com.cinebase.person.v1.PersonMessage
import com.cinebase.person.v1.PersonServiceGrpc
import com.cinebase.person.v1.RemoveCastMemberRequest
import com.cinebase.person.v1.RemoveCastMemberResponse
import com.cinebase.person.v1.RemoveCreatorRequest
import com.cinebase.person.v1.RemoveCreatorResponse
import com.cinebase.person.v1.SearchPeopleRequest
import com.cinebase.person.v1.SearchPeopleResponse
import com.cinebase.person.v1.UpdateCastMemberRequest
import com.cinebase.person.v1.UpdateCreatorRequest
import com.cinebase.person.v1.UpdatePersonRequest
import com.cinebase.personservice.cast.CastService
import com.cinebase.personservice.cast.MovieCast
import com.cinebase.personservice.creator.CreatorService
import com.cinebase.personservice.creator.MovieCreator
import com.cinebase.personservice.person.Person
import com.cinebase.personservice.person.PersonService
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Service
class PersonGrpcService(
    private val personService: PersonService,
    private val castService: CastService,
    private val creatorService: CreatorService,
) : PersonServiceGrpc.PersonServiceImplBase() {

    override fun getPerson(request: GetPersonRequest, observer: StreamObserver<PersonMessage>) {
        observer.respond { personService.get(request.id).toMessage() }
    }

    override fun searchPeople(request: SearchPeopleRequest, observer: StreamObserver<SearchPeopleResponse>) {
        observer.respond {
            val page = personService.list(request.query.takeUnless { it.isBlank() }, request.page, request.size)
            SearchPeopleResponse.newBuilder()
                .addAllPeople(page.content.map { it.toMessage() })
                .setTotal(page.totalElements)
                .build()
        }
    }

    override fun createPerson(request: CreatePersonRequest, observer: StreamObserver<PersonMessage>) {
        observer.respond {
            personService.create(request.name, request.biography.blankToNull(), request.birthDate.toLocalDateOrNull())
                .toMessage()
        }
    }

    override fun updatePerson(request: UpdatePersonRequest, observer: StreamObserver<PersonMessage>) {
        observer.respond {
            personService.update(request.id, request.name, request.biography.blankToNull(), request.birthDate.toLocalDateOrNull())
                .toMessage()
        }
    }

    override fun deletePerson(request: DeletePersonRequest, observer: StreamObserver<DeletePersonResponse>) {
        observer.respond {
            personService.delete(request.id)
            DeletePersonResponse.newBuilder().setDeleted(true).build()
        }
    }

    override fun getPeopleForMovie(request: GetPeopleForMovieRequest, observer: StreamObserver<GetPeopleForMovieResponse>) {
        observer.respond {
            val castMembers = castService.listForMovie(request.movieId)
            val creators = creatorService.listForMovie(request.movieId)
            val peopleById = personService.findAll(
                (castMembers.map { it.personId } + creators.map { it.personId }).toSet(),
            )

            GetPeopleForMovieResponse.newBuilder().apply {
                castMembers.forEach { member ->
                    peopleById[member.personId]?.let { addCast(member.toMessage(it)) }
                }
                creators.forEach { creator ->
                    peopleById[creator.personId]?.let { addCreators(creator.toMessage(it)) }
                }
            }.build()
        }
    }

    override fun addCastMember(request: AddCastMemberRequest, observer: StreamObserver<CastMemberMessage>) {
        observer.respond {
            val member = castService.add(request.movieId, request.personId, request.characterName)
            member.toMessage(personService.get(member.personId))
        }
    }

    override fun updateCastMember(request: UpdateCastMemberRequest, observer: StreamObserver<CastMemberMessage>) {
        observer.respond {
            val member = castService.updateCharacterName(request.id, request.characterName)
            member.toMessage(personService.get(member.personId))
        }
    }

    override fun removeCastMember(request: RemoveCastMemberRequest, observer: StreamObserver<RemoveCastMemberResponse>) {
        observer.respond {
            castService.remove(request.id)
            RemoveCastMemberResponse.newBuilder().setRemoved(true).build()
        }
    }

    override fun addCreator(request: AddCreatorRequest, observer: StreamObserver<CreatorMessage>) {
        observer.respond {
            val creator = creatorService.add(request.movieId, request.personId, request.job)
            creator.toMessage(personService.get(creator.personId))
        }
    }

    override fun updateCreator(request: UpdateCreatorRequest, observer: StreamObserver<CreatorMessage>) {
        observer.respond {
            val creator = creatorService.updateJob(request.id, request.job)
            creator.toMessage(personService.get(creator.personId))
        }
    }

    override fun removeCreator(request: RemoveCreatorRequest, observer: StreamObserver<RemoveCreatorResponse>) {
        observer.respond {
            creatorService.remove(request.id)
            RemoveCreatorResponse.newBuilder().setRemoved(true).build()
        }
    }
}

private inline fun <T> StreamObserver<T>.respond(block: () -> T) {
    try {
        onNext(block())
        onCompleted()
    } catch (e: Exception) {
        onError(e.toStatusRuntimeException())
    }
}

/**
 * Maps domain failures onto canonical gRPC statuses so the movie-service client can translate them
 * into the right GraphQL error type (NOT_FOUND vs BAD_REQUEST vs INTERNAL).
 */
private fun Exception.toStatusRuntimeException(): StatusRuntimeException = when (this) {
    is DateTimeParseException -> Status.INVALID_ARGUMENT.withDescription(message).withCause(this).asRuntimeException()
    is NoSuchElementException -> Status.NOT_FOUND.withDescription(message).withCause(this).asRuntimeException()
    is IllegalArgumentException -> Status.INVALID_ARGUMENT.withDescription(message).withCause(this).asRuntimeException()
    else -> Status.INTERNAL.withDescription(message).withCause(this).asRuntimeException()
}

private fun String.blankToNull(): String? = if (isBlank()) null else this

private fun String.toLocalDateOrNull(): LocalDate? =
    try {
        if (isBlank()) null else LocalDate.parse(this)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Invalid birth_date: $this", e)
    }

private fun Person.toMessage(): PersonMessage = PersonMessage.newBuilder()
    .setId(id)
    .setName(name)
    .setBiography(biography ?: "")
    .setBirthDate(birthDate?.toString() ?: "")
    .setCreatedAt(createdAt.toString())
    .setUpdatedAt(updatedAt.toString())
    .build()

private fun MovieCast.toMessage(person: Person): CastMemberMessage = CastMemberMessage.newBuilder()
    .setId(id)
    .setMovieId(movieId)
    .setPerson(person.toMessage())
    .setCharacterName(characterName)
    .build()

private fun MovieCreator.toMessage(person: Person): CreatorMessage = CreatorMessage.newBuilder()
    .setId(id)
    .setMovieId(movieId)
    .setPerson(person.toMessage())
    .setJob(job)
    .build()
