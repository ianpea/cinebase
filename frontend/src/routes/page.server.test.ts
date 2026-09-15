import {beforeEach, describe, expect, it, vi} from 'vitest';
import {load} from './+page.server';
import {MoviesDocument} from '$lib/graphql/queries';
import {makeMovie} from '$lib/testing/fixtures';
import {serverQuery} from '$lib/server/graphql';
import type {MoviePage} from '$lib/graphql/types';

vi.mock('$lib/server/graphql', () => ({serverQuery: vi.fn()}));

const mockedServerQuery = vi.mocked(serverQuery);

beforeEach(() => {
    mockedServerQuery.mockReset();
});

/** `load` only reads `fetch`, so the rest of the event can stay out of the way. */
function runLoad() {
    return load({fetch: vi.fn()} as unknown as Parameters<typeof load>[0]);
}

function moviePage(overrides: Partial<MoviePage> = {}): MoviePage {
    return {items: [makeMovie()], total: 1, page: 0, size: 12, totalPages: 1, ...overrides};
}

describe('Movies SSR load', () => {
    it('queries the first page with the defaults the page itself starts from', async () => {
        const page = moviePage();
        mockedServerQuery.mockResolvedValue({movies: page} as never);

        const data = await runLoad();

        expect(mockedServerQuery).toHaveBeenCalledExactlyOnceWith(expect.anything(), MoviesDocument, {
            search: null,
            sort: {field: 'CREATED_AT', direction: 'DESC'},
            page: 0,
            size: 12
        });
        // The page renders straight from this, so it must be the movie page and no error.
        expect(data).toEqual({movies: page, moviesError: null});
    });

    it('reports a backend failure as page data instead of throwing', async () => {
        mockedServerQuery.mockRejectedValue(new Error('movie-service is unreachable'));

        // A failed SSR query still renders the page, showing the client's retry button.
        await expect(runLoad()).resolves.toEqual({
            movies: null,
            moviesError: 'movie-service is unreachable'
        });
    });

    it('falls back to a generic message for a non-Error failure', async () => {
        mockedServerQuery.mockRejectedValue({});

        await expect(runLoad()).resolves.toEqual({
            movies: null,
            moviesError: 'Something went wrong. Please try again.'
        });
    });
});
