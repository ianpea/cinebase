import {createClient, fetchExchange, type AnyVariables, type TypedDocumentNode} from '@urql/core';
import {env} from '$env/dynamic/private';
import {unwrap, type QueryResult, type QueryVariables} from '$lib/graphql/result';

/**
 * GraphQL access for server-side rendering.
 *
 * The browser client (`$lib/graphql/client.ts`) uses the relative `url: '/graphql'`, which only
 * means something inside a browser page — in development Vite proxies it, in production
 * `src/hooks.server.ts` does. A server load has no page to resolve against, so this module sends
 * its requests straight to movie-service at `MOVIE_SERVICE_ORIGIN`: the same variable the proxy
 * hook uses, defaulting to `http://localhost:8081` for local development and set to
 * `http://movie-service:8081` in Docker Compose.
 *
 * This lives under `$lib/server/` on purpose: SvelteKit fails the build if browser code imports
 * it, so the backend origin cannot leak into the client bundle.
 */

/** Where movie-service listens when `MOVIE_SERVICE_ORIGIN` is not set (local development). */
const DEFAULT_ORIGIN = 'http://localhost:8081';

/** Absolute URL of the GraphQL endpoint, reachable from the server process. */
export function movieServiceGraphqlUrl(origin = env.MOVIE_SERVICE_ORIGIN ?? DEFAULT_ORIGIN): string {
    return new URL('/graphql', origin).toString();
}

/**
 * Runs one query from a load function and resolves with its data, throwing the same kind of
 * `Error` the browser client throws so the caller's `catch` can show one message either way.
 *
 * `fetch` is the load's `fetch`. No cache exchange is involved: a load runs once per request, so
 * there is nothing a cache could ever hit.
 */
export async function serverQuery<Doc extends TypedDocumentNode<any, any>>(
    fetch: typeof globalThis.fetch,
    document: Doc,
    variables: QueryVariables<Doc>
): Promise<QueryResult<Doc>> {
    const client = createClient({
        url: movieServiceGraphqlUrl(),
        fetch,
        // Spring GraphQL serves /graphql as POST-only; disable urql's GET fallback.
        preferGetMethod: false,
        requestPolicy: 'network-only',
        exchanges: [fetchExchange]
    });

    const result = await client.query(document, variables as AnyVariables).toPromise();
    return unwrap(result) as QueryResult<Doc>;
}
