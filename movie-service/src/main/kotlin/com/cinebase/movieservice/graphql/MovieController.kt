package com.cinebase.movieservice.graphql

import com.cinebase.movieservice.artwork.Artwork
import com.cinebase.movieservice.artwork.ArtworkService
import com.cinebase.movieservice.grpc.PersonClient
import com.cinebase.movieservice.movie.Movie
import com.cinebase.movieservice.movie.MovieService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

/**
 * GraphQL entry points served by movie-service.
 *
 * This covers the first vertical slice: a `movie(id)` query whose `cast` and `creators` fields are
 * resolved from person-service over gRPC, while the movie + artwork data comes from the local DB.
 */
@Controller
class MovieController(
    private val movies: MovieService,
    private val artworks: ArtworkService,
    private val people: PersonClient,
) {

    @QueryMapping
    fun movie(@Argument id: Long): Movie? = movies.find(id)

    @SchemaMapping(typeName = "Movie", field = "artworks")
    fun artworks(movie: Movie): List<Artwork> = artworks.listForMovie(movie.id)

    @SchemaMapping(typeName = "Movie", field = "cast")
    fun cast(movie: Movie): List<CastMemberDto> =
        people.getPeopleForMovie(movie.id).castList.map { it.toDto() }

    @SchemaMapping(typeName = "Movie", field = "creators")
    fun creators(movie: Movie): List<CreatorDto> =
        people.getPeopleForMovie(movie.id).creatorsList.map { it.toDto() }
}
