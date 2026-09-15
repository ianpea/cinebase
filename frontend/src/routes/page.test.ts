import {fireEvent, render, screen, waitFor} from '@testing-library/svelte';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import MoviesPage from './+page.svelte';
import {mutate, request} from '$lib/graphql/api';
import {makeMovie} from '$lib/testing/fixtures';
import type {Movie} from '$lib/graphql/types';

vi.mock('svelte-sonner', () => ({toast: {success: vi.fn(), error: vi.fn()}}));

vi.mock('$lib/graphql/api', async (importOriginal) => ({
    ...(await importOriginal<typeof import('$lib/graphql/api')>()),
    mutate: vi.fn(),
    request: vi.fn()
}));

const mockedRequest = vi.mocked(request);
const mockedMutate = vi.mocked(mutate);

beforeEach(() => {
    mockedRequest.mockReset();
    mockedMutate.mockReset();
});

function moviePage(items: Movie[], total = items.length) {
    return {
        movies: {
            items,
            total,
            page: 0,
            size: 12,
            totalPages: total === 0 ? 0 : Math.ceil(total / 12)
        }
    } as never;
}

describe('Movies page', () => {
    it('shows the loading state until the query resolves, then the grid', async () => {
        let resolveRequest: (value: unknown) => void = () => { };
        mockedRequest.mockReturnValueOnce(new Promise((resolve) => (resolveRequest = resolve)) as never);
        render(MoviesPage);

        expect(screen.getByText('Loading movies…')).toBeTruthy();

        resolveRequest(moviePage([makeMovie()]));

        await waitFor(() => expect(screen.getByText('Interstellar')).toBeTruthy());
        expect(screen.queryByText('Loading movies…')).toBeNull();
    });

    it('asks for the first page with the default sorting', async () => {
        mockedRequest.mockResolvedValue(moviePage([]));
        render(MoviesPage);

        await waitFor(() => expect(mockedRequest).toHaveBeenCalledOnce());

        expect(mockedRequest).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            search: null,
            sort: {field: 'CREATED_AT', direction: 'DESC'},
            page: 0,
            size: 12
        });
    });

    it('invites the user to add a movie when the library is empty', async () => {
        mockedRequest.mockResolvedValue(moviePage([]));
        render(MoviesPage);

        await waitFor(() => expect(screen.getByText('No movies yet')).toBeTruthy());
        // The header action and the empty-state action.
        expect(screen.getAllByRole('button', {name: /Add movie/})).toHaveLength(2);
    });

    it('renders a card per movie and the pagination summary', async () => {
        mockedRequest.mockResolvedValue(
            moviePage([makeMovie({id: '1', title: 'Interstellar'}), makeMovie({id: '2', title: 'Arrival'})], 14)
        );
        render(MoviesPage);

        await waitFor(() => expect(screen.getByText('Arrival')).toBeTruthy());

        expect(screen.getAllByRole('link', {name: 'Interstellar'})).toHaveLength(1);
        expect(screen.getByText('14 movies · page 1 of 2')).toBeTruthy();
    });

    it('shows the error state and retries the query', async () => {
        mockedRequest.mockRejectedValueOnce(new Error('movie-service is unreachable'));
        mockedRequest.mockResolvedValueOnce(moviePage([makeMovie({title: 'Interstellar'})]));
        render(MoviesPage);

        await waitFor(() => expect(screen.getByText('movie-service is unreachable')).toBeTruthy());

        await fireEvent.click(screen.getByRole('button', {name: 'Try again'}));

        await waitFor(() => expect(screen.getByText('Interstellar')).toBeTruthy());
        expect(mockedRequest).toHaveBeenCalledTimes(2);
    });
});
