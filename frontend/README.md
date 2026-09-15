# frontend

The Cinebase UI: a SvelteKit app in TypeScript that renders the movie and people pages and talks to
`movie-service` over GraphQL.

## Requirements

- Node.js 22+
- `movie-service` running on port 8081 (or `MOVIE_SERVICE_ORIGIN` pointing at it)

## Run it

```bash
npm install
npm run dev        # http://localhost:5173, proxies /graphql and /uploads to localhost:8081
npm run build      # production build via adapter-node
npm test           # 70 tests
npm run check      # svelte-check
```

| Variable | Default | Purpose |
| --- | --- | --- |
| `MOVIE_SERVICE_ORIGIN` | `http://localhost:8081` | where SSR loads and the production proxy find `movie-service` (in Compose: `http://movie-service:8081`) |

## Pages

| Route | Contents |
| --- | --- |
| `/` | movie grid with artwork, debounced search, sorting, pagination, add/edit dialogs, delete confirmation |
| `/movies/[id]` | metadata, artwork gallery (upload with preview and progress, remove), cast and creators with add/edit/remove |
| `/people` | people list, search, pagination, add/edit dialogs, delete confirmation |

Reusable pieces live in `src/lib/components/`: `MovieCard`, `MovieDialog`, `ArtworkUpload`,
`CastCreatorDialog` (shared by cast and creators), `PersonDialog`, `SearchInput`, `Pagination` and
the `Loading`/`Empty`/`Error` states.

## How data is fetched

```
src/lib/graphql/     documents, handwritten types, browser client, upload, shared result helpers
src/lib/server/      server-only GraphQL client used by the +page.server.ts loaders
src/hooks.server.ts  forwards /graphql and /uploads to movie-service in production
```

- **First paint is server-rendered.** `/`, `/people` and `/movies/[id]` each have a `+page.server.ts`
  that queries `movie-service` and returns the first page as data, so the HTML already contains the
  cards. A failure is returned as data too, so the page shows its own error state with a retry button
  instead of a blank screen.
- **Interactions stay in the browser.** Search, sorting, pagination and every refetch after a mutation
  go through the typed `request`/`mutate` wrappers in `src/lib/graphql/api.ts`, using the `@urql/core`
  client. `@urql/svelte`'s `queryStore` is not used because its variables are not reactive.
- **Artwork upload is hand-rolled.** urql's fetch exchange cannot emit a GraphQL multipart request, so
  `src/lib/graphql/upload.ts` sends one with `XMLHttpRequest` — which is also what gives the real
  upload progress bar. Files are validated in the browser (type, 10 MB) as well as on the server.

## Notes

- Runes mode is forced for project files in `vite.config.ts`.
- `csrf: { trustedOrigins: ['*'] }` is set there because SvelteKit's origin check otherwise rejects the
  multipart artwork upload when the browser's origin differs from `ORIGIN` (for example `127.0.0.1`
  instead of `localhost`). The app has no cookies or sessions, so the check protects nothing.
- `/graphql` and `/uploads` are not SvelteKit routes; keeping them relative is why `movie-service`
  needs no CORS configuration.
- The Docker image runs `adapter-node`, which needs `ORIGIN`, `PORT` and a raised `BODY_SIZE_LIMIT`
  (its 512 KB default is below the 10 MB artwork limit). Compose sets all three.
