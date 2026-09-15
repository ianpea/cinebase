import {createClient, fetchExchange, type AnyVariables, type TypedDocumentNode} from '@urql/core';
import {env} from '$env/dynamic/private';
import {unwrap, type QueryResult, type QueryVariables} from '$lib/graphql/result';

/**
 * SSR cannot resolve the browser's relative `/graphql`, so it calls movie-service directly at
 * MOVIE_SERVICE_ORIGIN — the same variable `src/hooks.server.ts` proxies browser requests to.
 * `$lib/server/` is deliberate: SvelteKit fails the build if browser code imports it, so the
 * backend origin cannot leak into the client bundle.
 */

/** Must mirror the fallback in `src/hooks.server.ts`. */
const DEFAULT_ORIGIN = 'http://localhost:8081';

export function movieServiceGraphqlUrl(origin = env.MOVIE_SERVICE_ORIGIN ?? DEFAULT_ORIGIN): string {
    return new URL('/graphql', origin).toString();
}

/**
 * Uses the load function's `fetch` and a per-call urql client so serialization, parsing and errors
 * match the browser client.
 */
export async function serverQuery<Doc extends TypedDocumentNode<any, any>>(
    fetch: typeof globalThis.fetch,
    document: Doc,
    variables: QueryVariables<Doc>
): Promise<QueryResult<Doc>> {
    const client = createClient({
        url: movieServiceGraphqlUrl(),
        fetch,
        // Spring GraphQL serves POST only; stop urql's GET fallback (405 otherwise).
        preferGetMethod: false,
        requestPolicy: 'network-only',
        exchanges: [fetchExchange]
    });

    const result = await client.query(document, variables as AnyVariables).toPromise();
    return unwrap(result) as QueryResult<Doc>;
}
