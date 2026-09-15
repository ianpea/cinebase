import type {PageServerLoad} from './$types';
import {
    DEFAULT_MOVIE_SORT_DIRECTION,
    DEFAULT_MOVIE_SORT_FIELD,
    MoviesDocument,
    PAGE_SIZE
} from '$lib/graphql/queries';
import {errorMessage} from '$lib/graphql/result';
import type {MoviesQueryVariables} from '$lib/graphql/types';
import {serverQuery} from '$lib/server/graphql';

/**
 * Renders the first page of the movie grid during SSR, so the initial HTML already contains the
 * movie cards instead of a loading placeholder. Everything the user does afterwards (search, sort,
 * pagination, and every refetch after a mutation) still runs in the browser through
 * `$lib/graphql/api.ts`.
 *
 * A failure is returned as data rather than thrown: a backend that is briefly unavailable should
 * still render the page, with the retry button the movies page already provides.
 */
export const load: PageServerLoad = async ({fetch}) => {
    const variables: MoviesQueryVariables = {
        // An empty search box means "no filter", which the API expresses as null.
        search: null,
        sort: {field: DEFAULT_MOVIE_SORT_FIELD, direction: DEFAULT_MOVIE_SORT_DIRECTION},
        page: 0,
        size: PAGE_SIZE
    };

    try {
        const data = await serverQuery(fetch, MoviesDocument, variables);
        return {movies: data.movies, moviesError: null};
    } catch(e) {
        return {movies: null, moviesError: errorMessage(e)};
    }
};
