# Cinebase

A small movie manager. A **SvelteKit** frontend talks to one **GraphQL** API served by
**movie-service** (Kotlin / Spring Boot), which resolves people, cast and creators from
**person-service** over **gRPC**. Each service persists to its own **PostgreSQL** database, and the
frontend talks only to **movie-service**.

| Layer | Stack |
| --- | --- |
| `frontend` | SvelteKit 2, Svelte 5 (runes), TypeScript, Tailwind 4 + shadcn-svelte, @urql/core |
| `movie-service` | Kotlin, Spring Boot 4, Spring GraphQL, gRPC client |
| `person-service` | Kotlin, Spring Boot 4, gRPC server |
| `data` | PostgreSQL 17, Hibernate / JPA |

## Features

| Assessment requirement | Implementation |
| --- | --- |
| List movies with artwork | Responsive movie grid with cover artwork or placeholder |
| Add, update, delete movies | Movie dialogs; deleting asks for confirmation |
| Upload, replace, remove artwork | `/movies/[id]` gallery for poster, backdrop and still, with client-side preview and real upload progress |
| List cast and creators | `/movies/[id]` cast and creators sections |
| Add, update, delete people and roles | People dialogs; one reusable dialog for both cast and creators |
| Search movies, cast and creators | Debounced search on movies and people, plus a person picker inside the role dialog |
| (+) Sorting and pagination | Movies sort by title, release year or creation date, ascending or descending; both list pages are paginated |

Every async flow reports its state: loading, empty, error with retry, saving, deleting, uploading,
validation and success toasts. Search is case-insensitive, and artwork is validated in the browser
for instant feedback and again on the server, which is what actually enforces the 10 MB and
PNG / JPEG / WEBP / GIF / AVIF limits.

## Architecture

```mermaid
flowchart TB
  ui(["Browser — Svelte 5 UI"])
  fe["SvelteKit server (adapter-node) :3000<br/>SSR loads + proxy for /graphql and /uploads"]
  ms["movie-service :8081<br/>GraphQL API — movies and artwork"]
  ps["person-service :9090 (gRPC)<br/>people, cast and creator roles"]
  mdb[("movie_service DB")]
  pdb[("person_service DB")]
  fs[["uploads/ on disk"]]

  ui -->|"GraphQL — POST /graphql"| fe
  fe -->|GraphQL| ms
  ms -->|gRPC| ps
  ms --> mdb
  ps --> pdb
  ms --> fs
```

Everything the browser requests goes through the SvelteKit server on port 3000, which server-renders
the first page and forwards `/graphql` and `/uploads` to `movie-service`.

| Service | Responsibility | Owns |
| --- | --- | --- |
| frontend | UI, SSR of the first page, same-origin proxy for `/graphql` and `/uploads` | — |
| `movie-service` | The only frontend-facing API: movies, artwork files, and the gRPC client for people data | `movies`, `artworks` |
| `person-service` | People and the roles that link them to movies — gRPC only, no HTTP API | `people`, `movie_cast`, `movie_creator` |

Each service owns its own database and does not access the other service's tables. Cross-service data, such as movie cast and creator information, is resolved through gRPC. For simplicity, both databases run in a single PostgreSQL container.

## Quick start

Requires Docker only.

```bash
docker compose up --build
```

Then open <http://localhost:3000>. Compose starts PostgreSQL, both services and the frontend, and the
second database is created on first start.

- `docker compose down` stops the stack and keeps the data.
- `docker compose down -v` also deletes the database volume.
- `docker compose up --build -d` after a code change — keep `--build`, or Compose reuses the
  existing image and keeps running the old code.

Postgres data lives on the `postgres-data` volume and artwork on the `./data/uploads` bind mount, so
both survive a rebuild.

### Building images

Run every build from the repository root, which is where `docker-compose.yml` lives.

```bash
docker compose build                     # build all three images
docker compose build movie-service       # rebuild one service
docker compose build person-service
docker compose build frontend
docker compose up -d                     # start what you just built
```

`docker compose build` on its own never starts a container, and `docker compose up` without
`--build` reuses the image that already exists instead of rebuilding it. Add `--no-cache` when a
dependency changed but the layer cache is still serving the old image:

```bash
docker compose build --no-cache frontend
```

| Service | Image |
| --- | --- |
| `frontend` | `cinebase-frontend` |
| `movie-service` | `cinebase-movie-service` |
| `person-service` | `cinebase-person-service` |

Both Kotlin services compile the shared [`proto/`](proto) directory, so Compose builds them with the
repository root as their context; the frontend builds from `./frontend`.

| Service | Port | Notes |
| --- | --- | --- |
| frontend | 3000 | the only port you need |
| movie-service | 8081 | GraphQL at `POST /graphql`, artwork at `GET /uploads/**` |
| person-service | 9090 | gRPC only, called by movie-service |
| person-service | 8082 | HTTP port is bound but exposes no API |
| postgres | 5432 | two databases: `movie_service`, `person_service` |

Every value has a default in `docker-compose.yml`; `.env.example` lists the overrides.

### Running without Docker

Requires **JDK 21**, **Node 22+** and a local PostgreSQL.

```bash
psql postgres -c "CREATE ROLE cinebase LOGIN PASSWORD 'cinebase';"
createdb -O cinebase movie_service
createdb -O cinebase person_service
```

```bash
cd person-service && ./gradlew bootRun            # gRPC on 9090
cd movie-service  && ./gradlew bootRun            # GraphQL on 8081
cd frontend       && npm install && npm run dev   # http://localhost:5173
```

Both services default to `localhost` in `application.yml`, so no environment variables are needed. If
your default JDK is newer than 21, point `JAVA_HOME` at a JDK 21 first.

## API and service communication

### GraphQL — frontend to movie-service

`POST http://localhost:8081/graphql`, POST only, since Spring GraphQL does not serve GET. The schema
is [`schema.graphqls`](movie-service/src/main/resources/graphql/schema.graphqls), and the browser
reaches it on the relative path `/graphql` through the SvelteKit server.

```graphql
query {
  movies(search: "nolan", sort: {field: TITLE, direction: ASC}, page: 0, size: 12) {
    total
    totalPages
    items {
      id
      title
      releaseYear
      genre
      artworkUrl
    }
  }
}

mutation {
  createMovie(input: {title: "Arrival", releaseYear: 2016, genre: "Sci-Fi"}) {
    id
    title
  }
}
```

Queries cover listing, a single movie and people; mutations cover movie, person, cast, creator and
artwork operations. Domain failures are mapped to `NOT_FOUND`, `BAD_REQUEST` or `INTERNAL_ERROR`, so
the UI can show one clear message per failure.

### gRPC — movie-service to person-service

Contract: [`person-service.proto`](proto/cinebase/person/v1/person-service.proto). `person-service` is
the server and `movie-service` the client.

| Group | RPCs |
| --- | --- |
| People | `GetPerson`, `SearchPeople`, `CreatePerson`, `UpdatePerson`, `DeletePerson` |
| Roles (read) | `GetPeopleForMovie` — cast and creators for one movie in a single call |
| Cast | `AddCastMember`, `UpdateCastMember`, `RemoveCastMember` |
| Creators | `AddCreator`, `UpdateCreator`, `RemoveCreator` |

In Compose the channel target is the service name (`person-service:9090`), never `localhost`.

### Where the data lives

| Data | Storage |
| --- | --- |
| Movies | `movies` table in the `movie_service` database |
| Artwork metadata | `artworks` table — movie, public URL, type (`POSTER`, `BACKDROP`, `STILL`) |
| Artwork files | local disk under `uploads/artworks/` (host: `./data/uploads`), served at `/uploads/**` by `movie-service` |
| People, cast and creators | `people`, `movie_cast` and `movie_creator` in the `person_service` database |

## Important implementation decisions

**Hybrid SSR and browser interactions.** Initial movie, people and movie-detail pages are rendered with data fetched server-side from `movie-service`, avoiding an empty loading state on first paint. Search, sorting, pagination and mutation refetches then use the same GraphQL API from the browser.

**Same-origin frontend API.** Browser requests use relative `/graphql` and `/uploads` paths, which SvelteKit proxies to `movie-service`. Server-side rendering calls the service directly, keeping internal service addresses out of browser code and avoiding the need for CORS configuration.

**Service and data ownership.** `movie-service` and `person-service` each own their own database and do not access each other's tables. Cross-service data such as cast and creator information is resolved through gRPC.

**Bounded gRPC calls.** Calls to `person-service` use a 5-second deadline so an unavailable service cannot leave GraphQL requests waiting indefinitely. Retries and circuit breaking are intentionally outside the scope of the project.

**GraphQL artwork uploads with progress.** Artwork is uploaded through the GraphQL multipart specification. Browser upload events provide real progress feedback while files are being transferred.

**Safe artwork deletion.** Artwork files are deleted only after the corresponding database transaction commits. This prevents a rollback from leaving database records pointing to files that have already been removed.

**Person identity and role integrity.** People are checked for duplicates using a trimmed, case-insensitive name together with birth date. Deleting a person also removes their associated cast and creator roles within the same transaction. Creating a role also requires an existing movie: because `movie-service` owns movies, it checks the id itself and answers `NOT_FOUND` before any gRPC call, so `person-service` never needs to know what a valid movie is.

**Consistent movie artwork.** A movie's cover is kept in sync with its artwork records, including promoting another artwork when the current cover is removed.


## Testing

**239 tests, all passing.**

| Suite | Tests | Stack |
| --- | --- | --- |
| `movie-service` | 80 | JUnit, MockK, AssertJ, `GraphQlTester`, in-process gRPC, H2 |
| `person-service` | 84 | JUnit, MockK, AssertJ, in-process gRPC, H2 |
| `frontend` | 75 | Vitest, `@testing-library/svelte`, jsdom |

```bash
cd person-service && ./gradlew test
cd movie-service  && ./gradlew test
cd frontend       && npm test
cd frontend       && npm run check    # svelte-check
```

| Suite | What the tests cover |
| --- | --- |
| `movie-service` | Movie CRUD, search, sorting and pagination clamps; artwork validation, replacement, removal and cover promotion; movie-existence checks for artwork and role writes; upload storage, after-commit file cleanup and path-traversal refusals; GraphQL end to end via `GraphQlTester`; the gRPC client against an in-process server and its per-call deadline |
| `person-service` | CRUD and edge cases for people, cast and creators; duplicate person rejection; case-insensitive search; role cleanup on person deletion; proto ↔ domain mapping; the gRPC status contract (`NOT_FOUND`, `INVALID_ARGUMENT`, `INTERNAL`); the full gRPC → JPA stack on H2 |
| `frontend` | Page rendering from SSR data, loading / empty / error states, dialog validation and payloads, role dialog search and edit mode, artwork validation and upload progress, and the SSR loaders |

Integration tests run against H2 in PostgreSQL mode, writing artwork to a throwaway directory, so
they need no database and leave nothing behind.

## Known limitations

* **No authentication or authorisation.** Authentication and user-specific access control are outside the scope of the project.
* **Artwork uses local filesystem storage.** A multi-instance deployment would require shared or object storage, together with cleanup for orphaned files.
* **Database schemas use Hibernate `ddl-auto: update`.** A production deployment would use versioned migrations such as Flyway.
* **Both service databases share one PostgreSQL container.** The schemas remain independently owned, while the deployment is kept simple for the assessment.
* **Cast and creator lists are not paginated.** They are expected to remain small, and duplicate cast assignments are currently allowed.
* **Artwork supports poster, backdrop and still types** with add, replace and remove operations, but no reordering and no person photos.
* **gRPC resilience is limited to a 5-second deadline.** Retries, circuit breaking and graceful degradation are not implemented.

## AI usage

GitHub Copilot in VS Code (agent mode) was used throughout development based on a predefined implementation plan and architecture.

AI assisted with the initial implementation across the Spring Boot services, GraphQL and gRPC integration, Svelte frontend, Docker configuration and automated tests. Generated code was reviewed, run and refined as the application was integrated, including simplifying frontend state handling and removing low-value tests.

The final implementation was verified with **239 automated tests**, frontend type checking, a clean Docker Compose build, and manual end-to-end testing covering movie and people management, search, sorting, pagination, gRPC integration, artwork upload and removal, error handling and persistence across restarts.

