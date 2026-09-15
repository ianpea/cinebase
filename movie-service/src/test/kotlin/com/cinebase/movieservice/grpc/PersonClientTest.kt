package com.cinebase.movieservice.grpc

import com.cinebase.person.v1.AddCastMemberRequest
import com.cinebase.person.v1.CastMemberMessage
import com.cinebase.person.v1.GetPeopleForMovieRequest
import com.cinebase.person.v1.GetPeopleForMovieResponse
import com.cinebase.person.v1.PersonMessage
import com.cinebase.person.v1.PersonServiceGrpc
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PersonClientTest {

    private var server: Server? = null

    private fun clientFor(service: PersonServiceGrpc.PersonServiceImplBase): PersonClient {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(service)
            .build()
            .start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        return PersonClient(PersonServiceGrpc.newBlockingStub(channel))
    }

    @AfterEach
    fun tearDown() {
        server?.shutdownNow()
    }

    @Test
    fun `getPeopleForMovie maps cast with embedded person`() {
        val client = clientFor(object : PersonServiceGrpc.PersonServiceImplBase() {
            override fun getPeopleForMovie(
                request: GetPeopleForMovieRequest,
                responseObserver: StreamObserver<GetPeopleForMovieResponse>,
            ) {
                val person = PersonMessage.newBuilder().setId(7).setName("Matthew McConaughey").build()
                val cast = CastMemberMessage.newBuilder()
                    .setId(1)
                    .setMovieId(request.movieId)
                    .setPerson(person)
                    .setCharacterName("Cooper")
                    .build()
                responseObserver.onNext(GetPeopleForMovieResponse.newBuilder().addCast(cast).build())
                responseObserver.onCompleted()
            }
        })

        val response = client.getPeopleForMovie(42L)

        assertThat(response.castCount).isEqualTo(1)
        assertThat(response.getCast(0).characterName).isEqualTo("Cooper")
        assertThat(response.getCast(0).person.name).isEqualTo("Matthew McConaughey")
        assertThat(response.getCast(0).movieId).isEqualTo(42L)
    }

    @Test
    fun `addCastMember sends correct request fields`() {
        var movieId = -1L
        var personId = -1L
        var character = ""

        val client = clientFor(object : PersonServiceGrpc.PersonServiceImplBase() {
            override fun addCastMember(
                request: AddCastMemberRequest,
                responseObserver: StreamObserver<CastMemberMessage>,
            ) {
                movieId = request.movieId
                personId = request.personId
                character = request.characterName
                val person = PersonMessage.newBuilder().setId(request.personId).setName("X").build()
                responseObserver.onNext(
                    CastMemberMessage.newBuilder()
                        .setId(9)
                        .setMovieId(request.movieId)
                        .setPerson(person)
                        .setCharacterName(request.characterName)
                        .build(),
                )
                responseObserver.onCompleted()
            }
        })

        client.addCastMember(5L, 10L, "Cooper")

        assertThat(movieId).isEqualTo(5L)
        assertThat(personId).isEqualTo(10L)
        assertThat(character).isEqualTo("Cooper")
    }

    @Test
    fun `a gRPC failure is propagated unchanged`() {
        val client = clientFor(object : PersonServiceGrpc.PersonServiceImplBase() {
            override fun getPeopleForMovie(
                request: GetPeopleForMovieRequest,
                responseObserver: StreamObserver<GetPeopleForMovieResponse>,
            ) {
                responseObserver.onError(
                    Status.NOT_FOUND.withDescription("Movie ${request.movieId} is unknown").asRuntimeException(),
                )
            }
        })

        // PersonClient must not swallow the status: GraphQlExceptionAdvice turns it into a
        // GraphQL error whose type matches the gRPC code.
        val failure = assertThrows<StatusRuntimeException> { client.getPeopleForMovie(42L) }

        assertThat(failure.status.code).isEqualTo(Status.Code.NOT_FOUND)
        assertThat(failure.status.description).isEqualTo("Movie 42 is unknown")
    }
}
