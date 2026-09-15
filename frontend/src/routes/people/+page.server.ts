import type {PageServerLoad} from './$types';
import {PAGE_SIZE, PeopleDocument} from '$lib/graphql/queries';
import {errorMessage} from '$lib/graphql/result';
import type {PeopleQueryVariables} from '$lib/graphql/types';
import {serverQuery} from '$lib/server/graphql';

/**
 * Same shape as the movies route: the first page of people is rendered during SSR, and search,
 * pagination and post-mutation refetches stay in the browser.
 *
 * A failure is returned as data so the page can show its own error state with a retry button.
 */
export const load: PageServerLoad = async ({fetch}) => {
    const variables: PeopleQueryVariables = {
        // An empty search box means "no filter", which the API expresses as null.
        search: null,
        page: 0,
        size: PAGE_SIZE
    };

    try {
        const data = await serverQuery(fetch, PeopleDocument, variables);
        return {people: data.people, peopleError: null};
    } catch(e) {
        return {people: null, peopleError: errorMessage(e)};
    }
};
