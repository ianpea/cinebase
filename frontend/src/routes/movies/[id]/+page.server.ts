import type {PageServerLoad} from './$types';
import {MovieDetailDocument} from '$lib/graphql/queries';
import {errorMessage} from '$lib/graphql/result';
import {serverQuery} from '$lib/server/graphql';

/**
 * Renders a movie (with its artwork, cast and creators) during SSR, so a deep link shows the
 * movie immediately. SvelteKit re-runs this load on every navigation to the route, including
 * client-side ones, so switching between movie ids never needs a separate browser query.
 *
 * A failure is returned as data so the page can show its own error state with a retry button.
 */
export const load: PageServerLoad = async ({fetch, params}) => {
    try {
        const data = await serverQuery(fetch, MovieDetailDocument, {id: params.id});
        return {movie: data.movie, movieError: null};
    } catch(e) {
        return {movie: null, movieError: errorMessage(e)};
    }
};
