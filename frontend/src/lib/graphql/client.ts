import { cacheExchange, createClient, fetchExchange } from '@urql/core';

/**
 * urql client for the SvelteKit frontend.
 *
 * The GraphQL API is served by movie-service. During local development the
 * `/graphql` path is proxied by Vite to `http://localhost:8081` (see
 * `vite.config.ts`), which avoids CORS entirely.
 *
 * The client is created from `@urql/core` — the same runtime `@urql/svelte` builds on — because
 * `@urql/svelte`'s `queryStore`/`mutationStore` capture `variables` once at creation and every
 * page here is driven by reactive variables (search, sort, page, route id). `api.ts` wraps the
 * client so pages stay typed and only need `try/catch`.
 */
export const client = createClient({
	url: '/graphql',
	// Spring GraphQL serves /graphql as POST-only; disable urql's GET fallback.
	preferGetMethod: false,
	// Every mutation is followed by a refetch, so cached reads would only show stale data.
	requestPolicy: 'network-only',
	exchanges: [cacheExchange, fetchExchange]
});
