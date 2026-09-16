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

/** Loads the initial movies for server-side rendering. */
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
