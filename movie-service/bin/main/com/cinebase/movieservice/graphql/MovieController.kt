package com.cinebase.movieservice.graphql

import com.cinebase.movieservice.artwork.Artwork
import com.cinebase.movieservice.artwork.ArtworkService
import com.cinebase.movieservice.artwork.ArtworkType
import com.cinebase.movieservice.grpc.PersonClient
import com.cinebase.movieservice.movie.Movie
import com.cinebase.movieservice.movie.MovieService
import jakarta.validation.Valid
import org.springframework.data.domain.Sort
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import org.springframework.web.multipart.MultipartFile

/**
 * Movie queries and mutations served by movie-service.
 *
 * Movie + artwork data comes from the local database; `cast` and `creators` are resolved from
 * person-service over gRPC, so the frontend only ever talks to this one GraphQL API.
 */
@Controller
class MovieController(
    private val movieService: MovieService,
    private val artworkService: ArtworkService,
    private val personClient: PersonClient,
) {

    // --- Queries ---

    @QueryMapping
    fun movie(@Argument id: Long): Movie? = movieService.find(id)

    @QueryMapping
    fun movies(
        @Argument search: String?,
        @Argument sort: MovieSortInput?,
        @Argument page: Int?,
        @Argument size: Int?,
    ): MoviePageDto {
        val pageNumber = (page ?: 0).coerceAtLeast(0)
        val pageSize = (size ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val result = movieService.list(
            search = search,
            sort = sort?.toSort() ?: DEFAULT_SORT,
            page = pageNumber,
            size = pageSize,
        )
        return MoviePageDto(
            items = result.content,
            total = result.totalElements.toInt(),
            page = result.number,
            size = result.size,
            totalPages = result.totalPages,
        )
    }

    // --- Movie mutations ---

    @MutationMapping
    fun createMovie(@Argument @Valid input: MovieInput): Movie =
        movieService.create(input.title, input.synopsis, input.releaseYear, input.genre)

    @MutationMapping
    fun updateMovie(@Argument id: Long, @Argument @Valid input: MovieInput): Movie =
        movieService.update(id, input.title, input.synopsis, input.releaseYear, input.genre)

    @MutationMapping
    fun deleteMovie(@Argument id: Long): Boolean {
        movieService.delete(id)
        return true
    }

    // --- Artwork mutations ---

    @MutationMapping
    fun uploadMovieArtwork(
        @Argument movieId: Long,
        @Argument type: ArtworkType,
        @Argument file: MultipartFile,
    ): Artwork = artworkService.store(movieId, type, file)

    @MutationMapping
    fun removeMovieArtwork(@Argument id: Long): Boolean {
        artworkService.remove(id)
        return true
    }

    // --- Movie field resolvers ---

    @SchemaMapping(typeName = "Movie", field = "artworks")
    fun artworks(movie: Movie): List<Artwork> = artworkService.listForMovie(movie.id)

    @SchemaMapping(typeName = "Movie", field = "cast")
    fun cast(movie: Movie): List<CastMemberDto> =
        personClient.getPeopleForMovie(movie.id).castList.map { it.toDto() }

    @SchemaMapping(typeName = "Movie", field = "creators")
    fun creators(movie: Movie): List<CreatorDto> =
        personClient.getPeopleForMovie(movie.id).creatorsList.map { it.toDto() }

    companion object {
        val DEFAULT_SORT: Sort = Sort.by(Sort.Direction.DESC, "createdAt")
    }
}
