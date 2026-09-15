import {describe, expect, it, vi} from 'vitest';
import {MoviesDocument} from '$lib/graphql/queries';

// The SSR client resolves its endpoint from the same private variable the proxy hook uses.
vi.mock('$env/dynamic/private', () => ({env: {MOVIE_SERVICE_ORIGIN: 'http://movie-service:8081'}}));

import {movieServiceGraphqlUrl, serverQuery} from './graphql';

/** A `fetch` stand-in typed like the real one, so the recorded call arguments stay usable. */
function fetchStub(payload: unknown, status = 200) {
    return vi.fn(async (_url: string, _init: RequestInit) => {
        return new Response(JSON.stringify(payload), {
            status,
            headers: {'content-type': 'application/json'}
        });
    });
}

const moviePage = {items: [], total: 0, page: 0, size: 12, totalPages: 0};
const variables = {
    search: null,
    sort: {field: 'CREATED_AT' as const, direction: 'DESC' as const},
    page: 0,
    size: 12
};

describe('serverQuery', () => {
    it('posts to movie-service directly, not through a relative URL', async () => {
        const fetchMock = fetchStub({data: {movies: moviePage}});

        const data = await serverQuery(
            fetchMock as unknown as typeof globalThis.fetch,
            MoviesDocument,
            variables
        );

        expect(data.movies).toEqual(moviePage);

        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe('http://movie-service:8081/graphql');
        expect(init.method).toBe('POST');

        const body = JSON.parse(init.body as string);
        expect(body.query).toContain('query Movies');
        expect(body.variables).toEqual(variables);
    });

    it('throws the GraphQL message the browser client would show', async () => {
        const fetchMock = fetchStub({errors: [{message: 'Movie service is overloaded.'}]});

        await expect(
            serverQuery(fetchMock as unknown as typeof globalThis.fetch, MoviesDocument, variables)
        ).rejects.toThrow('Movie service is overloaded.');
    });

    it('rejects rather than resolving with an unusable body', async () => {
        // A body without `data` is a network error for urql, so an SSR load fails loudly instead
        // of rendering an empty page.
        const fetchMock = fetchStub({});

        await expect(
            serverQuery(fetchMock as unknown as typeof globalThis.fetch, MoviesDocument, variables)
        ).rejects.toThrow();
    });
});

describe('movieServiceGraphqlUrl', () => {
    it('appends the GraphQL path to the configured origin', () => {
        expect(movieServiceGraphqlUrl('http://backend:9000')).toBe('http://backend:9000/graphql');
    });

    it('falls back to the configured MOVIE_SERVICE_ORIGIN', () => {
        expect(movieServiceGraphqlUrl()).toBe('http://movie-service:8081/graphql');
    });
});
