package com.cinebase.movieservice.grpc

import com.cinebase.person.v1.PersonServiceGrpc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory

@Configuration
class GrpcStubConfig {

    @Bean
    fun personStub(channels: GrpcChannelFactory): PersonServiceGrpc.PersonServiceBlockingStub =
        PersonServiceGrpc.newBlockingStub(channels.createChannel("person"))
}
