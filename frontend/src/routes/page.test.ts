import {fireEvent, render, screen, waitFor} from '@testing-library/svelte';
import {tick} from 'svelte';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import MoviesPage from './+page.svelte';
import type {PageData} from './$types';
import {mutate, request} from '$lib/graphql/api';
import {makeMovie} from '$lib/testing/fixtures';
import type {Movie, MoviePage} from '$lib/graphql/types';

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

function moviePage(items: Movie[], total = items.length): MoviePage {
    return {items, total, page: 0, size: 12, totalPages: total === 0 ? 0 : Math.ceil(total / 12)};
}

/** The result the browser client's `request` resolves with. */
function moviesResponse(items: Movie[], total = items.length) {
    return {movies: moviePage(items, total)} as never;
}

/** The data `+page.server.ts` hands to the page, so the first render is the server's result. */
function ssrPage(items: Movie[]): PageData {
    return {movies: moviePage(items), moviesError: null};
}

function renderPage(data: PageData) {
    // SvelteKit hands every page `params`, `data` and `form`; this route has no params or actions.
    return render(MoviesPage, {props: {params: {}, data, form: null}});
}

describe('Movies page', () => {
    it('renders the movies the server load already fetched, without querying again', async () => {
        renderPage(ssrPage([makeMovie({title: 'Interstellar'})]));
        await tick();

        expect(screen.getByText('Interstellar')).toBeTruthy();
        expect(screen.queryByText('Loading movies…')).toBeNull();
        // The point of the server load: hydration must not repeat the query it just performed.
        expect(mockedRequest).not.toHaveBeenCalled();
    });

    it('renders a card per movie and the pagination summary from the server data', () => {
        renderPage(
            ssrPage([
                makeMovie({id: '1', title: 'Interstellar'}),
                makeMovie({id: '2', title: 'Arrival'})
            ])
        );

        expect(screen.getAllByRole('link', {name: 'Interstellar'})).toHaveLength(1);
        expect(screen.getByText('2 movies')).toBeTruthy();
    });

    it('invites the user to add a movie when the library is empty', () => {
        renderPage(ssrPage([]));

        expect(screen.getByText('No movies yet')).toBeTruthy();
        // The header action and the empty-state action.
        expect(screen.getAllByRole('button', {name: /Add movie/})).toHaveLength(2);
    });

    it('queries the server from the browser when the user paginates', async () => {
        mockedRequest.mockResolvedValue(moviesResponse([makeMovie({title: 'Arrival'})], 14));
        const movies = Array.from({length: 14}, (_, i) =>
            makeMovie({id: String(i), title: `Movie ${i}`})
        );
        renderPage(ssrPage(movies));

        expect(screen.getByText('14 movies · page 1 of 2')).toBeTruthy();

        await fireEvent.click(screen.getByRole('button', {name: /Next/}));

        await waitFor(() => expect(screen.getByText('Arrival')).toBeTruthy());
        expect(mockedRequest).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            search: null,
            sort: {field: 'CREATED_AT', direction: 'DESC'},
            page: 1,
            size: 12
        });
    });

    it('queries the server from the browser when the user searches', async () => {
        mockedRequest.mockResolvedValue(moviesResponse([makeMovie({title: 'Batman Begins'})]));
        renderPage(ssrPage([makeMovie({title: 'Interstellar'})]));

        await fireEvent.input(screen.getByLabelText('Search movies by title…'), {
            target: {value: 'batman'}
        });

        await waitFor(() => expect(screen.getByText('Batman Begins')).toBeTruthy());
        expect(mockedRequest).toHaveBeenCalledExactlyOnceWith(expect.anything(), {
            search: 'batman',
            sort: {field: 'CREATED_AT', direction: 'DESC'},
            page: 0,
            size: 12
        });
    });

    it('restarts from the first page when the search changes while paged, in one request', async () => {
        mockedRequest
            .mockResolvedValueOnce(moviesResponse([makeMovie({title: 'Page Two Movie'})], 30))
            .mockResolvedValueOnce(moviesResponse([makeMovie({title: 'Batman Begins'})]));
        const movies = Array.from({length: 30}, (_, i) =>
            makeMovie({id: String(i), title: `Movie ${i}`})
        );
        renderPage(ssrPage(movies));

        await fireEvent.click(screen.getByRole('button', {name: /Next/}));
        await waitFor(() => expect(screen.getByText('Page Two Movie')).toBeTruthy());

        await fireEvent.input(screen.getByLabelText('Search movies by title…'), {
            target: {value: 'batman'}
        });
        await waitFor(() => expect(screen.getByText('Batman Begins')).toBeTruthy());

        // Paging, then the search: exactly two requests, and the new search starts at page 0.
        expect(mockedRequest).toHaveBeenCalledTimes(2);
        expect(mockedRequest).toHaveBeenLastCalledWith(expect.anything(), {
            search: 'batman',
            sort: {field: 'CREATED_AT', direction: 'DESC'},
            page: 0,
            size: 12
        });
    });

    it('shows the server load failure and retries it from the browser', async () => {
        mockedRequest.mockResolvedValue(moviesResponse([makeMovie({title: 'Interstellar'})]));
        renderPage({movies: null, moviesError: 'movie-service is unreachable'});
        await tick();

        expect(screen.getByText('movie-service is unreachable')).toBeTruthy();
        // A failed server load must not be retried automatically on hydration either.
        expect(mockedRequest).not.toHaveBeenCalled();

        await fireEvent.click(screen.getByRole('button', {name: 'Try again'}));

        await waitFor(() => expect(screen.getByText('Interstellar')).toBeTruthy());
        expect(mockedRequest).toHaveBeenCalledOnce();
    });

    it('keeps the loading state for a browser-side query', async () => {
        let resolveRequest: (value: unknown) => void = () => { };
        mockedRequest.mockReturnValueOnce(
            new Promise((resolve) => (resolveRequest = resolve)) as never
        );

        renderPage({movies: null, moviesError: 'movie-service is unreachable'});
        await fireEvent.click(screen.getByRole('button', {name: 'Try again'}));

        expect(screen.getByText('Loading movies…')).toBeTruthy();

        resolveRequest(moviesResponse([makeMovie()]));

        await waitFor(() => expect(screen.getByText('Interstellar')).toBeTruthy());
        expect(screen.queryByText('Loading movies…')).toBeNull();
    });
});
