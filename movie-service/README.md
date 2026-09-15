# movie-service

The GraphQL API for Cinebase, and the only backend the frontend talks to.

It owns movies and their artwork, and it is a **gRPC client** of `person-service` — people, cast and
creators are never read from a local table. It is also the HTTP origin for uploaded artwork files.

```
frontend ──GraphQL──> movie-service ──gRPC──> person-service
                            │
                        movies, artworks
```

## Requirements

- JDK 21
- PostgreSQL 16+
- No local Gradle — the wrapper is included (`./gradlew`)

## Configuration

Everything has a working default, so local development needs no environment variables.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/movie_service` | database connection |
| `DB_USERNAME` | `cinebase` | database user |
| `DB_PASSWORD` | `cinebase` | database password |
| `GRPC_HOST` | `localhost` | where `person-service` is reachable (in Compose: `person-service`) |
| `GRPC_PORT` | `9090` | `person-service` gRPC port |
| `GRPC_DEADLINE` | `5s` | ceiling on every `person-service` RPC |
| `UPLOADS_DIR` | `uploads` | directory for artwork files (in Compose: `/app/uploads`) |

## Run it

```bash
./gradlew bootRun     # GraphQL on http://localhost:8081/graphql
./gradlew test        # 80 tests
./gradlew bootJar     # build the runnable jar
```

## GraphQL

- Endpoint: `POST http://localhost:8081/graphql` — POST only, Spring GraphQL does not serve GET.
- Schema: [`src/main/resources/graphql/schema.graphqls`](src/main/resources/graphql/schema.graphqls)

The controllers in `graphql/` map straight onto the services; `GraphQlExceptionAdvice` turns domain
failures into the right GraphQL error types (`NOT_FOUND`, `BAD_REQUEST`, `INTERNAL_ERROR`) so the
frontend can show one clear message per failure.

Artwork uploads use the GraphQL multipart request spec (`operations`, `map`, `0` parts) on the same
endpoint. Spring GraphQL does not support that spec itself, so `multipart-spring-graphql` adds it.
The frontend sends the same shape by hand with `XMLHttpRequest`, which is also what makes the upload
progress bar possible.

## Data ownership

This service owns two tables in the `movie_service` database and nothing else:

| Table | Contents |
| --- | --- |
| `movies` | title, synopsis, release year, genre, cover `artworkUrl`, timestamps |
| `artworks` | movie id, public URL, type (`POSTER`, `BACKDROP`, `STILL`) |

No table here references people. `movieId` values stored in `person-service` are plain numbers, so
the two databases stay independent — which is also why the cast list on the movie page is proof that
gRPC is doing the work.

## gRPC client role

`GrpcStubConfig` creates a blocking stub from the Spring gRPC `GrpcChannelFactory`; `PersonClient`
wraps it and converts gRPC status codes into domain exceptions.

- Proto contract: [`../proto/cinebase/person/v1/person-service.proto`](../proto/cinebase/person/v1/person-service.proto)
- Channel target: `spring.grpc.client.channel.person.target` = `${GRPC_HOST}:${GRPC_PORT}`
- Calls used: `GetPeopleForMovie` (movie detail), `SearchPeople`, person CRUD, and the cast/creator
  add-update-remove RPCs.
- Deadline: `spring.grpc.client.channel.person.default.deadline` = `${GRPC_DEADLINE:5s}`. Spring gRPC
  applies its `DefaultDeadlineSetupClientInterceptor` to the channel, so every call gets the deadline
  and a `person-service` that stops answering fails with `DEADLINE_EXCEEDED` instead of holding the
  GraphQL request open forever. There is still no retry and no circuit breaker.

The property name matters: `spring.grpc.client.channels.<name>.address` is silently ignored and the
channel then treats the name as a DNS host.

## Artwork storage

- Files are written to `$UPLOADS_DIR/artworks/<uuid>.<ext>` and served from `/uploads/**` by a
  resource handler in `WebConfig`. Only the public URL is stored in the database.
- Limits: 10 MB, and PNG, JPEG, WEBP, GIF or AVIF. The browser checks first for instant feedback; the
  server check is the one that counts.
- Stored file names are random UUIDs, and deletes resolve the path and refuse anything that escapes
  the uploads directory, so a bad database value can never remove an unrelated file.
- A movie's cover (`artworkUrl`) is kept in step with its artwork rows, so removing the cover promotes
  the next artwork instead of leaving a broken image.
- **File deletions are not part of the database transaction.** A local disk cannot join a
  PostgreSQL commit, so deleting a movie or an artwork only *schedules* its files: the rows are
  dropped inside the transaction and `UploadStorage` removes the files from disk after it commits,
  logging rather than throwing if the disk refuses. The two can therefore never fail in the
  expensive direction — a rolled-back delete leaves every file exactly where it was, so a surviving
  row always still points at a real file — and the worst case is an unreferenced file left on disk,
  which no client can see.

## Tests

```bash
./gradlew test        # 80 tests, 6 classes
```

| File | Tests | Focus |
| --- | --- | --- |
| `movie/MovieServiceTest` | 21 | create/trim, update, delete, search, pagination clamps, sorting, cover sync |
| `artwork/ArtworkServiceTest` | 15 | upload gate (empty, oversize, wrong type), extension mapping, cover promotion, removal |
| `config/UploadStorageTest` | 9 | unique names, byte round-trip, deletes, after-commit timing, path-traversal refusals |
| `grpc/PersonClientTest` | 3 | in-process gRPC: response mapping, request mapping, error propagation |
| `grpc/PersonClientDeadlineTest` | 1 | a silent `person-service` is ended by the configured deadline, not by hanging |
| `MovieServiceGraphQlIntegrationTest` | 31 | GraphQL end to end via `GraphQlTester` on H2, with a mocked gRPC client — CRUD, validation, search, pagination, sorting, artwork, role delegation and the error contract |

Integration tests run against H2 in PostgreSQL mode with files written to `build/test-uploads`, so
they need no database and leave nothing behind.
