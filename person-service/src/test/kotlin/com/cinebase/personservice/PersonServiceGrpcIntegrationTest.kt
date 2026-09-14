package com.cinebase.personservice

import com.cinebase.person.v1.AddCreatorRequest
import com.cinebase.person.v1.GetPeopleForMovieRequest
import com.cinebase.person.v1.PersonServiceGrpc
import com.cinebase.personservice.cast.CastService
import com.cinebase.personservice.creator.CreatorService
import com.cinebase.personservice.person.PersonService
import io.grpc.ManagedChannelBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PersonServiceGrpcIntegrationTest @Autowired constructor(
    private val lifecycle: GrpcServerLifecycle,
    private val people: PersonService,
    private val cast: CastService,
    private val creators: CreatorService,
) {

    private val port: Int
        get() = lifecycle.port

    @Test
    fun `getPeopleForMovie returns cast and creators over gRPC`() {
        val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
        try {
            val stub = PersonServiceGrpc.newBlockingStub(channel)

            val nolan = people.create("Christopher Nolan", "Director", null)
            val mcconaughey = people.create("Matthew McConaughey", "Actor", null)
            cast.add(1L, mcconaughey.id, "Cooper")
            creators.add(1L, nolan.id, "Director")

            val response = stub.getPeopleForMovie(
                GetPeopleForMovieRequest.newBuilder().setMovieId(1L).build(),
            )

            assertThat(response.castCount).isEqualTo(1)
            assertThat(response.getCast(0).characterName).isEqualTo("Cooper")
            assertThat(response.getCast(0).person.name).isEqualTo("Matthew McConaughey")

            assertThat(response.creatorsCount).isEqualTo(1)
            assertThat(response.getCreators(0).job).isEqualTo("Director")
            assertThat(response.getCreators(0).person.name).isEqualTo("Christopher Nolan")
        } finally {
            channel.shutdown()
        }
    }

    @Test
    fun `addCreator via gRPC persists a creator role`() {
        val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
        try {
            val stub = PersonServiceGrpc.newBlockingStub(channel)
            val person = people.create("Denis Villeneuve", "Director", null)

            val created = stub.addCreator(
                AddCreatorRequest.newBuilder()
                    .setMovieId(2L)
                    .setPersonId(person.id)
                    .setJob("Director")
                    .build(),
            )

            assertThat(created.job).isEqualTo("Director")
            assertThat(created.person.name).isEqualTo("Denis Villeneuve")
            assertThat(creators.listForMovie(2L)).hasSize(1)
        } finally {
            channel.shutdown()
        }
    }
}
