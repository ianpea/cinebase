# frontend

The Cinebase UI: a SvelteKit app in TypeScript that renders the movie and people pages and talks to
`movie-service` over GraphQL.

## Requirements

- Node.js 22+ and `movie-service` on port 8081 (or `MOVIE_SERVICE_ORIGIN` pointing at it)

## Run it

```bash
npm install
npm run dev        # http://localhost:5173, proxies /graphql and /uploads to localhost:8081
npm run build      # production build via adapter-node
npm test           # 75 tests
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

## How data is fetched

- **First paint is server-rendered.** `/`, `/people` and `/movies/[id]` each have a `+page.server.ts`
  that queries `movie-service` through `src/lib/server/graphql.ts` and returns the first page as data,
  so the HTML already contains the cards. A failure is returned as data too, so the page shows its own
  error state instead of a blank screen.
- **Interactions stay in the browser.** Search, sorting, pagination and every refetch after a mutation
  go through the typed `request`/`mutate` wrappers in `src/lib/graphql/api.ts`, backed by `@urql/core`.
- **Artwork uploads use the GraphQL multipart request spec.** `src/lib/graphql/upload.ts` sends them
  with `XMLHttpRequest`, which is also what makes the real upload progress bar possible. Files are
  validated in the browser (type, 10 MB) as well as on the server.

Browser requests stay relative: `src/hooks.server.ts` forwards `/graphql` and `/uploads` to
`MOVIE_SERVICE_ORIGIN`, and `npm run dev` gets the same proxy from Vite, so `movie-service` needs no
CORS configuration. The Docker image runs `adapter-node`, which needs `ORIGIN`, `PORT` and a raised
`BODY_SIZE_LIMIT` (its 512 KB default is below the 10 MB artwork limit); Compose sets all three.
`vite.config.ts` forces runes mode and disables SvelteKit's CSRF origin check, which would otherwise
reject the multipart upload when the browser's origin differs from `ORIGIN`.
