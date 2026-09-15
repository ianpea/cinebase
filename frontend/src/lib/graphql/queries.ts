import {gql, type TypedDocumentNode} from '@urql/core';
import type {
	AddCastMemberMutation,
	AddCastMemberVariables,
	AddCreatorMutation,
	AddCreatorVariables,
	CreateMovieMutation,
	CreateMovieVariables,
	CreatePersonMutation,
	CreatePersonVariables,
	DeleteMovieMutation,
	DeleteMovieVariables,
	DeletePersonMutation,
	DeletePersonVariables,
	MovieDetailQuery,
	MovieDetailQueryVariables,
	MovieSortField,
	MoviesQuery,
	MoviesQueryVariables,
	PeopleQuery,
	PeopleQueryVariables,
	RemoveCastMemberMutation,
	RemoveCastMemberVariables,
	RemoveCreatorMutation,
	RemoveCreatorVariables,
	RemoveMovieArtworkMutation,
	RemoveMovieArtworkVariables,
	SortDirection,
	UpdateCastMemberMutation,
	UpdateCastMemberVariables,
	UpdateCreatorMutation,
	UpdateCreatorVariables,
	UpdateMovieMutation,
	UpdateMovieVariables,
	UpdatePersonMutation,
	UpdatePersonVariables
} from './types';

/**
 * GraphQL documents for the Cinebase UI.
 *
 * All operations hit movie-service. Cast/creator/person data is resolved there over gRPC to
 * person-service, so the frontend never talks to person-service directly.
 *
 * Each document is declared as a `TypedDocumentNode` so `request`/`mutate` infer both the
 * variables and the result type from the document itself.
 */

// --- First-page defaults ---
// Shared by the SSR loads (`+page.server.ts`) and the pages themselves: the server renders the
// first page of a list with exactly these values, and the page state starts from the same ones,
// so hydrating never asks the API for something different than what it already received.

/** Rows per page for the movie and people lists. */
export const PAGE_SIZE = 12;

/** Movies are shown newest first until the user picks another sort. */
export const DEFAULT_MOVIE_SORT_FIELD: MovieSortField = 'CREATED_AT';
export const DEFAULT_MOVIE_SORT_DIRECTION: SortDirection = 'DESC';

const MOVIE_SUMMARY = gql`
	fragment MovieSummary on Movie {
		id
		title
		synopsis
		releaseYear
		genre
		artworkUrl
		createdAt
		updatedAt
	}
`;

const CAST_FIELDS = gql`
	fragment CastFields on MovieCast {
		id
		movieId
		characterName
		person {
			id
			name
		}
	}
`;

const CREATOR_FIELDS = gql`
	fragment CreatorFields on MovieCreator {
		id
		movieId
		job
		person {
			id
			name
		}
	}
`;

const ARTWORK_FIELDS = gql`
	fragment ArtworkFields on Artwork {
		id
		movieId
		url
		type
	}
`;

const PERSON_FIELDS = gql`
	fragment PersonFields on Person {
		id
		name
		biography
		birthDate
		createdAt
		updatedAt
	}
`;

/** Paginated movie grid on `/`. */
export const MoviesDocument: TypedDocumentNode<MoviesQuery, MoviesQueryVariables> = gql`
	${MOVIE_SUMMARY}
	query Movies($search: String, $sort: MovieSort, $page: Int, $size: Int) {
		movies(search: $search, sort: $sort, page: $page, size: $size) {
			items {
				...MovieSummary
			}
			total
			page
			size
			totalPages
		}
	}
`;

/** Single movie with artworks, cast and creators on `/movies/[id]`. */
export const MovieDetailDocument: TypedDocumentNode<MovieDetailQuery, MovieDetailQueryVariables> =
	gql`
		${MOVIE_SUMMARY}
		${CAST_FIELDS}
		${CREATOR_FIELDS}
		${ARTWORK_FIELDS}
		query MovieDetail($id: ID!) {
			movie(id: $id) {
				...MovieSummary
				artworks {
					...ArtworkFields
				}
				cast {
					...CastFields
				}
				creators {
					...CreatorFields
				}
			}
		}
	`;

/** Paginated people list on `/people`. */
export const PeopleDocument: TypedDocumentNode<PeopleQuery, PeopleQueryVariables> = gql`
	${PERSON_FIELDS}
	query People($search: String, $page: Int, $size: Int) {
		people(search: $search, page: $page, size: $size) {
			items {
				...PersonFields
			}
			total
			page
			size
			totalPages
		}
	}
`;

export const CreateMovieDocument: TypedDocumentNode<CreateMovieMutation, CreateMovieVariables> = gql`
	${MOVIE_SUMMARY}
	mutation CreateMovie($input: MovieInput!) {
		createMovie(input: $input) {
			...MovieSummary
		}
	}
`;

export const UpdateMovieDocument: TypedDocumentNode<UpdateMovieMutation, UpdateMovieVariables> = gql`
	${MOVIE_SUMMARY}
	mutation UpdateMovie($id: ID!, $input: MovieInput!) {
		updateMovie(id: $id, input: $input) {
			...MovieSummary
		}
	}
`;

export const DeleteMovieDocument: TypedDocumentNode<DeleteMovieMutation, DeleteMovieVariables> = gql`
	mutation DeleteMovie($id: ID!) {
		deleteMovie(id: $id)
	}
`;

export const CreatePersonDocument: TypedDocumentNode<CreatePersonMutation, CreatePersonVariables> =
	gql`
		${PERSON_FIELDS}
		mutation CreatePerson($input: PersonInput!) {
			createPerson(input: $input) {
				...PersonFields
			}
		}
	`;

export const UpdatePersonDocument: TypedDocumentNode<UpdatePersonMutation, UpdatePersonVariables> =
	gql`
		${PERSON_FIELDS}
		mutation UpdatePerson($id: ID!, $input: PersonInput!) {
			updatePerson(id: $id, input: $input) {
				...PersonFields
			}
		}
	`;

export const DeletePersonDocument: TypedDocumentNode<DeletePersonMutation, DeletePersonVariables> =
	gql`
		mutation DeletePerson($id: ID!) {
			deletePerson(id: $id)
		}
	`;

export const AddCastMemberDocument: TypedDocumentNode<AddCastMemberMutation, AddCastMemberVariables> =
	gql`
		${CAST_FIELDS}
		mutation AddCastMember($input: CastInput!) {
			addCastMember(input: $input) {
				...CastFields
			}
		}
	`;

export const UpdateCastMemberDocument: TypedDocumentNode<
	UpdateCastMemberMutation,
	UpdateCastMemberVariables
> = gql`
	${CAST_FIELDS}
	mutation UpdateCastMember($id: ID!, $input: CastInput!) {
		updateCastMember(id: $id, input: $input) {
			...CastFields
		}
	}
`;

export const RemoveCastMemberDocument: TypedDocumentNode<
	RemoveCastMemberMutation,
	RemoveCastMemberVariables
> = gql`
	mutation RemoveCastMember($id: ID!) {
		removeCastMember(id: $id)
	}
`;

export const AddCreatorDocument: TypedDocumentNode<AddCreatorMutation, AddCreatorVariables> = gql`
	${CREATOR_FIELDS}
	mutation AddCreator($input: CreatorInput!) {
		addCreator(input: $input) {
			...CreatorFields
		}
	}
`;

export const UpdateCreatorDocument: TypedDocumentNode<UpdateCreatorMutation, UpdateCreatorVariables> =
	gql`
		${CREATOR_FIELDS}
		mutation UpdateCreator($id: ID!, $input: CreatorInput!) {
			updateCreator(id: $id, input: $input) {
				...CreatorFields
			}
		}
	`;

export const RemoveCreatorDocument: TypedDocumentNode<RemoveCreatorMutation, RemoveCreatorVariables> =
	gql`
		mutation RemoveCreator($id: ID!) {
			removeCreator(id: $id)
		}
	`;

export const RemoveMovieArtworkDocument: TypedDocumentNode<
	RemoveMovieArtworkMutation,
	RemoveMovieArtworkVariables
> = gql`
	mutation RemoveMovieArtwork($id: ID!) {
		removeMovieArtwork(id: $id)
	}
`;
