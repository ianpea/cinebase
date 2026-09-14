import {cacheExchange, createClient, fetchExchange} from '@urql/core';

/**
 * urql client for the SvelteKit frontend.
 *
 * The GraphQL API is served by movie-service. During local development the
 * `/graphql` path is proxied by Vite to `http://localhost:8081` (see
 * `vite.config.ts`), which avoids CORS entirely.
 */
export const client = createClient({
    url: '/graphql',
    // Spring GraphQL serves /graphql as POST-only; disable urql's GET fallback.
    preferGetMethod: false,
    exchanges: [cacheExchange, fetchExchange]
});
