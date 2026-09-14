package com.cinebase.movieservice.grpc

import com.cinebase.person.v1.AddCastMemberRequest
import com.cinebase.person.v1.AddCreatorRequest
import com.cinebase.person.v1.CastMemberMessage
import com.cinebase.person.v1.CreatePersonRequest
import com.cinebase.person.v1.CreatorMessage
import com.cinebase.person.v1.DeletePersonRequest
import com.cinebase.person.v1.GetPeopleForMovieRequest
import com.cinebase.person.v1.GetPeopleForMovieResponse
import com.cinebase.person.v1.GetPersonRequest
import com.cinebase.person.v1.PersonMessage
import com.cinebase.person.v1.PersonServiceGrpc
import com.cinebase.person.v1.RemoveCastMemberRequest
import com.cinebase.person.v1.RemoveCreatorRequest
import com.cinebase.person.v1.SearchPeopleRequest
import com.cinebase.person.v1.SearchPeopleResponse
import com.cinebase.person.v1.UpdateCastMemberRequest
import com.cinebase.person.v1.UpdateCreatorRequest
import com.cinebase.person.v1.UpdatePersonRequest
import org.springframework.stereotype.Service

/** gRPC client used by movie-service to reach person-service for all people/cast/creator data. */
@Service
class PersonClient(private val stub: PersonServiceGrpc.PersonServiceBlockingStub) {

    fun getPerson(id: Long): PersonMessage =
        stub.getPerson(GetPersonRequest.newBuilder().setId(id).build())

    fun searchPeople(query: String?, page: Int, size: Int): SearchPeopleResponse =
        stub.searchPeople(
            SearchPeopleRequest.newBuilder()
                .setQuery(query ?: "")
                .setPage(page)
                .setSize(size)
                .build(),
        )

    fun createPerson(name: String, biography: String?, birthDate: String?): PersonMessage =
        stub.createPerson(
            CreatePersonRequest.newBuilder()
                .setName(name)
                .setBiography(biography ?: "")
                .setBirthDate(birthDate ?: "")
                .build(),
        )

    fun updatePerson(id: Long, name: String, biography: String?, birthDate: String?): PersonMessage =
        stub.updatePerson(
            UpdatePersonRequest.newBuilder()
                .setId(id)
                .setName(name)
                .setBiography(biography ?: "")
                .setBirthDate(birthDate ?: "")
                .build(),
        )

    fun deletePerson(id: Long): Boolean =
        stub.deletePerson(DeletePersonRequest.newBuilder().setId(id).build()).deleted

    fun getPeopleForMovie(movieId: Long): GetPeopleForMovieResponse =
        stub.getPeopleForMovie(GetPeopleForMovieRequest.newBuilder().setMovieId(movieId).build())

    fun addCastMember(movieId: Long, personId: Long, characterName: String): CastMemberMessage =
        stub.addCastMember(
            AddCastMemberRequest.newBuilder()
                .setMovieId(movieId)
                .setPersonId(personId)
                .setCharacterName(characterName)
                .build(),
        )

    fun updateCastMember(id: Long, characterName: String): CastMemberMessage =
        stub.updateCastMember(
            UpdateCastMemberRequest.newBuilder()
                .setId(id)
                .setCharacterName(characterName)
                .build(),
        )

    fun removeCastMember(id: Long): Boolean =
        stub.removeCastMember(RemoveCastMemberRequest.newBuilder().setId(id).build()).removed

    fun addCreator(movieId: Long, personId: Long, job: String): CreatorMessage =
        stub.addCreator(
            AddCreatorRequest.newBuilder()
                .setMovieId(movieId)
                .setPersonId(personId)
                .setJob(job)
                .build(),
        )

    fun updateCreator(id: Long, job: String): CreatorMessage =
        stub.updateCreator(
            UpdateCreatorRequest.newBuilder()
                .setId(id)
                .setJob(job)
                .build(),
        )

    fun removeCreator(id: Long): Boolean =
        stub.removeCreator(RemoveCreatorRequest.newBuilder().setId(id).build()).removed
}
