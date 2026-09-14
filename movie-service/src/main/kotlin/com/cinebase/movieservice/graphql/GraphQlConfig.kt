package com.cinebase.movieservice.graphql

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Registers the custom scalars declared in `schema.graphqls`.
 *
 * - [dateTimeScalar] maps to [Instant] (used by `createdAt` / `updatedAt`).
 * - [dateScalar] maps to [LocalDate] (used by `birthDate`).
 * - [uploadScalar] is an input-only pass-through for multipart file uploads.
 */
@Configuration
class GraphQlConfig {

    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer = RuntimeWiringConfigurer { builder ->
        builder.scalar(dateTimeScalar)
        builder.scalar(dateScalar)
        builder.scalar(uploadScalar)
    }

    companion object {

        val dateTimeScalar: GraphQLScalarType = GraphQLScalarType.newScalar()
            .name("DateTime")
            .description("ISO-8601 instant")
            .coercing(object : Coercing<Instant, String> {
                override fun serialize(input: Any, graphQLContext: GraphQLContext, locale: Locale): String =
                    when (input) {
                        is Instant -> DateTimeFormatter.ISO_INSTANT.format(input)
                        is String -> input
                        else -> throw CoercingSerializeException("Expected Instant but was: $input")
                    }

                override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): Instant =
                    when (input) {
                        is String -> runCatching { Instant.parse(input) }.getOrElse {
                            throw CoercingParseValueException("Invalid DateTime: $input")
                        }
                        else -> throw CoercingParseValueException("Expected String but was: $input")
                    }

                override fun parseLiteral(
                    input: Value<*>,
                    variables: CoercedVariables,
                    graphQLContext: GraphQLContext,
                    locale: Locale,
                ): Instant {
                    val value = (input as? StringValue)?.value
                        ?: throw CoercingParseLiteralException("Expected a string literal")
                    return runCatching { Instant.parse(value) }.getOrElse {
                        throw CoercingParseLiteralException("Invalid DateTime: $value")
                    }
                }
            })
            .build()

        val dateScalar: GraphQLScalarType = GraphQLScalarType.newScalar()
            .name("Date")
            .description("ISO-8601 local date (yyyy-MM-dd)")
            .coercing(object : Coercing<LocalDate, String> {
                override fun serialize(input: Any, graphQLContext: GraphQLContext, locale: Locale): String =
                    when (input) {
                        is LocalDate -> input.toString()
                        is String -> input
                        else -> throw CoercingSerializeException("Expected LocalDate but was: $input")
                    }

                override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): LocalDate =
                    when (input) {
                        is String -> runCatching { LocalDate.parse(input) }.getOrElse {
                            throw CoercingParseValueException("Invalid Date: $input")
                        }
                        else -> throw CoercingParseValueException("Expected String but was: $input")
                    }

                override fun parseLiteral(
                    input: Value<*>,
                    variables: CoercedVariables,
                    graphQLContext: GraphQLContext,
                    locale: Locale,
                ): LocalDate {
                    val value = (input as? StringValue)?.value
                        ?: throw CoercingParseLiteralException("Expected a string literal")
                    return runCatching { LocalDate.parse(value) }.getOrElse {
                        throw CoercingParseLiteralException("Invalid Date: $value")
                    }
                }
            })
            .build()

        val uploadScalar: GraphQLScalarType = GraphQLScalarType.newScalar()
            .name("Upload")
            .description("Binary file upload (input only)")
            .coercing(object : Coercing<Any, Any> {
                override fun serialize(input: Any, graphQLContext: GraphQLContext, locale: Locale): Any =
                    throw CoercingSerializeException("Upload is an input-only scalar")

                override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): Any = input

                override fun parseLiteral(
                    input: Value<*>,
                    variables: CoercedVariables,
                    graphQLContext: GraphQLContext,
                    locale: Locale,
                ): Any = throw CoercingParseLiteralException("Upload cannot be a literal")
            })
            .build()
    }
}
