package com.cinebase.movieservice.grpc

import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetAddress
import java.net.ServerSocket

/** Boots the real context so the `person` channel is the one Spring gRPC builds from configuration. */
@SpringBootTest
@ActiveProfiles("test")
class PersonClientDeadlineTest {

    @Autowired
    private lateinit var personClient: PersonClient

    @Test
    @Timeout(15)
    fun `a person-service that never answers fails on the deadline`() {
        val failure = assertThrows<StatusRuntimeException> { personClient.getPeopleForMovie(1L) }

        assertThat(failure.status.code).isEqualTo(Status.Code.DEADLINE_EXCEEDED)
    }

    companion object {

        /** Accepts the TCP connection but never speaks HTTP/2, so only the deadline can end the call. */
        private val silentServer = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

        @JvmStatic
        @DynamicPropertySource
        fun personChannel(registry: DynamicPropertyRegistry) {
            registry.add("spring.grpc.client.channel.person.target") { "localhost:${silentServer.localPort}" }
            registry.add("spring.grpc.client.channel.person.default.deadline") { "200ms" }
        }

        @JvmStatic
        @AfterAll
        fun stopSilentServer() {
            silentServer.close()
        }
    }
}
