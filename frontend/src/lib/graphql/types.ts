// Handwritten GraphQL types mirroring movie-service's schema (no codegen).

export type ArtworkType = 'POSTER' | 'BACKDROP' | 'STILL';
export type MovieSortField = 'TITLE' | 'RELEASE_YEAR' | 'CREATED_AT';
export type SortDirection = 'ASC' | 'DESC';

export interface Artwork {
	id: string;
	movieId: string;
	url: string;
	type: ArtworkType;
}

export interface Person {
	id: string;
	name: string;
	biography?: string | null;
	birthDate?: string | null;
	createdAt: string;
	updatedAt: string;
}

export interface MovieCast {
	id: string;
	movieId: string;
	person: Person;
	characterName: string;
}

export interface MovieCreator {
	id: string;
	movieId: string;
	person: Person;
	job: string;
}

export interface Movie {
	id: string;
	title: string;
	synopsis?: string | null;
	releaseYear?: number | null;
	genre?: string | null;
	artworkUrl?: string | null;
	artworks: Artwork[];
	cast: MovieCast[];
	creators: MovieCreator[];
	createdAt: string;
	updatedAt: string;
}

/** Pagination envelope shared by `MoviePage` and `PersonPage`. */
export interface PageInfo {
	total: number;
	page: number;
	size: number;
	totalPages: number;
}

export interface MoviePage extends PageInfo {
	items: Movie[];
}

export interface PersonPage extends PageInfo {
	items: Person[];
}

// --- Input objects ---

export interface MovieInput {
	title: string;
	synopsis?: string | null;
	releaseYear?: number | null;
	genre?: string | null;
}

export interface PersonInput {
	name: string;
	biography?: string | null;
	birthDate?: string | null;
}

export interface CastInput {
	movieId: string;
	personId: string;
	characterName: string;
}

export interface CreatorInput {
	movieId: string;
	personId: string;
	job: string;
}

export interface MovieSortInput {
	field: MovieSortField;
	direction: SortDirection;
}

// --- Query variables / results ---
// Variables are `type` aliases (not `interface`) so they satisfy urql's `AnyVariables`
// (`Record<string, any>`) constraint via implicit index signatures.

export type MoviesQueryVariables = {
	search?: string | null;
	sort?: MovieSortInput | null;
	page?: number | null;
	size?: number | null;
};

export interface MoviesQuery {
	movies: MoviePage;
}

export type MovieDetailQueryVariables = {
	id: string;
};

export interface MovieDetailQuery {
	movie: Movie | null;
}

export type PeopleQueryVariables = {
	search?: string | null;
	page?: number | null;
	size?: number | null;
};

export interface PeopleQuery {
	people: PersonPage;
}

// --- Mutation variables / results ---

export type CreateMovieVariables = {
	input: MovieInput;
};

export interface CreateMovieMutation {
	createMovie: Movie;
}

export type UpdateMovieVariables = {
	id: string;
	input: MovieInput;
};

export interface UpdateMovieMutation {
	updateMovie: Movie;
}

export type DeleteMovieVariables = {
	id: string;
};

export interface DeleteMovieMutation {
	deleteMovie: boolean;
}

export type CreatePersonVariables = {
	input: PersonInput;
};

export interface CreatePersonMutation {
	createPerson: Person;
}

export type UpdatePersonVariables = {
	id: string;
	input: PersonInput;
};

export interface UpdatePersonMutation {
	updatePerson: Person;
}

export type DeletePersonVariables = {
	id: string;
};

export interface DeletePersonMutation {
	deletePerson: boolean;
}

export type AddCastMemberVariables = {
	input: CastInput;
};

export interface AddCastMemberMutation {
	addCastMember: MovieCast;
}

export type UpdateCastMemberVariables = {
	id: string;
	input: CastInput;
};

export interface UpdateCastMemberMutation {
	updateCastMember: MovieCast;
}

export type RemoveCastMemberVariables = {
	id: string;
};

export interface RemoveCastMemberMutation {
	removeCastMember: boolean;
}

export type AddCreatorVariables = {
	input: CreatorInput;
};

export interface AddCreatorMutation {
	addCreator: MovieCreator;
}

export type UpdateCreatorVariables = {
	id: string;
	input: CreatorInput;
};

export interface UpdateCreatorMutation {
	updateCreator: MovieCreator;
}

export type RemoveCreatorVariables = {
	id: string;
};

export interface RemoveCreatorMutation {
	removeCreator: boolean;
}

export type RemoveMovieArtworkVariables = {
	id: string;
};

export interface RemoveMovieArtworkMutation {
	removeMovieArtwork: boolean;
}
