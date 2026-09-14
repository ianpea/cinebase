package com.cinebase.movieservice.graphql

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import jakarta.validation.ConstraintViolationException
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler
import org.springframework.graphql.execution.ErrorType
import org.springframework.validation.BindException
import org.springframework.web.bind.annotation.ControllerAdvice

/**
 * Turns domain, validation and gRPC-client failures into meaningful GraphQL errors.
 *
 * `ErrorType` values map onto the standard GraphQL error extensions, so the frontend can
 * distinguish "not found" from "bad input" without parsing messages.
 */
@ControllerAdvice
class GraphQlExceptionAdvice {

    @GraphQlExceptionHandler
    fun handleNotFound(exception: NoSuchElementException, errorBuilder: GraphqlErrorBuilder<*>): GraphQLError =
        errorBuilder
            .errorType(ErrorType.NOT_FOUND)
            .message(exception.message ?: "Resource not found")
            .build()

    @GraphQlExceptionHandler
    fun handleValidation(exception: ConstraintViolationException, errorBuilder: GraphqlErrorBuilder<*>): GraphQLError =
        errorBuilder
            .errorType(ErrorType.BAD_REQUEST)
            .message(
                exception.constraintViolations
                    .sortedBy { it.propertyPath.toString() }
                    .joinToString("; ") { it.message },
            )
            .build()

    @GraphQlExceptionHandler
    fun handleBinding(exception: BindException, errorBuilder: GraphqlErrorBuilder<*>): GraphQLError =
        errorBuilder
            .errorType(ErrorType.BAD_REQUEST)
            .message(exception.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" })
            .build()

    @GraphQlExceptionHandler
    fun handleBadRequest(exception: IllegalArgumentException, errorBuilder: GraphqlErrorBuilder<*>): GraphQLError =
        errorBuilder
            .errorType(ErrorType.BAD_REQUEST)
            .message(exception.message ?: "Invalid request")
            .build()

    @GraphQlExceptionHandler
    fun handleGrpcFailure(exception: StatusRuntimeException, errorBuilder: GraphqlErrorBuilder<*>): GraphQLError {
        val errorType = when (exception.status.code) {
            Status.Code.NOT_FOUND -> ErrorType.NOT_FOUND
            Status.Code.INVALID_ARGUMENT, Status.Code.ALREADY_EXISTS -> ErrorType.BAD_REQUEST
            else -> ErrorType.INTERNAL_ERROR
        }
        return errorBuilder
            .errorType(errorType)
            .message(exception.status.description ?: "person-service request failed")
            .build()
    }
}
