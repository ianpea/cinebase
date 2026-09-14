// Handwritten GraphQL types mirroring movie-service's schema (no codegen).

export type ArtworkType = 'POSTER' | 'BACKDROP' | 'STILL';

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

export interface MovieDetailQuery {
    movie: Movie | null;
}

export interface MovieDetailQueryVariables {
    id: string;
}
