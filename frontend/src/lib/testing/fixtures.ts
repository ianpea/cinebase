import type { Artwork, Movie, Person } from '$lib/graphql/types';

/**
 * Fixture builders for the component tests.
 *
 * Only test files import this module, so it never ends up in the production bundle.
 */

export function makePerson(overrides: Partial<Person> = {}): Person {
	return {
		id: '7',
		name: 'Matthew McConaughey',
		biography: 'American actor',
		birthDate: '1969-11-04',
		createdAt: '2024-01-01T00:00:00Z',
		updatedAt: '2024-01-01T00:00:00Z',
		...overrides
	};
}

export function makeMovie(overrides: Partial<Movie> = {}): Movie {
	return {
		id: '1',
		title: 'Interstellar',
		synopsis: 'A trip through a wormhole.',
		releaseYear: 2014,
		genre: 'Sci-Fi',
		artworkUrl: '/uploads/artworks/poster.png',
		artworks: [],
		cast: [],
		creators: [],
		createdAt: '2024-01-01T00:00:00Z',
		updatedAt: '2024-01-01T00:00:00Z',
		...overrides
	};
}

export function makeArtwork(overrides: Partial<Artwork> = {}): Artwork {
	return {
		id: '10',
		movieId: '1',
		url: '/uploads/artworks/poster.png',
		type: 'POSTER',
		...overrides
	};
}
