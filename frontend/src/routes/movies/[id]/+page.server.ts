import type {PageServerLoad} from './$types';
import {MovieDetailDocument} from '$lib/graphql/queries';
import {errorMessage} from '$lib/graphql/result';
import {serverQuery} from '$lib/server/graphql';

export const load: PageServerLoad = async ({fetch, params}) => {
    try {
        const data = await serverQuery(fetch, MovieDetailDocument, {id: params.id});
        return {movie: data.movie, movieError: null};
    } catch(e) {
        return {movie: null, movieError: errorMessage(e)};
    }
};
