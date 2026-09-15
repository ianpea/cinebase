# movie-service

The GraphQL API for Cinebase and the only backend the frontend talks to. It owns movies and artwork,
serves the uploaded files, and reads people, cast and creators from `person-service` over gRPC.

## Configuration and run

Requires JDK 21 and PostgreSQL 16+; the Gradle wrapper is included and every setting below has a default.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/movie_service` | database connection |
| `DB_USERNAME`, `DB_PASSWORD` | `cinebase` | database credentials |
| `GRPC_HOST`, `GRPC_PORT` | `localhost`, `9090` | where `person-service` is reachable (in Compose: `person-service:9090`) |
| `GRPC_DEADLINE` | `5s` | ceiling on every `person-service` RPC |
| `UPLOADS_DIR` | `uploads` | directory for artwork files (in Compose: `/app/uploads`) |

```bash
./gradlew bootRun     # GraphQL on http://localhost:8081/graphql
./gradlew test        # 78 tests
./gradlew bootJar     # build the runnable jar
```

## GraphQL

- Endpoint: `POST http://localhost:8081/graphql` — POST only, Spring GraphQL does not serve GET.
- Schema: [`src/main/resources/graphql/schema.graphqls`](src/main/resources/graphql/schema.graphqls)

The controllers in `graphql/` delegate to the services, and `GraphQlExceptionAdvice` maps domain
failures to `NOT_FOUND`, `BAD_REQUEST` or `INTERNAL_ERROR` so the frontend can show one message per
failure. Artwork uploads use the GraphQL multipart request spec on the same endpoint, added by
`multipart-spring-graphql`.

## Data ownership

| Table | Contents |
| --- | --- |
| `movies` | title, synopsis, release year, genre, cover `artworkUrl`, timestamps |
| `artworks` | movie id, public URL, type (`POSTER`, `BACKDROP`, `STILL`) |

Nothing here references people: `movieId` values are plain numbers in `person-service`, so the two
databases stay independent. Artwork writes check that their movie exists.

## gRPC client role

`PersonClient` wraps a blocking stub and converts gRPC status codes into domain exceptions.

- Proto contract: [`../proto/cinebase/person/v1/person-service.proto`](../proto/cinebase/person/v1/person-service.proto)
- Channel target: `spring.grpc.client.channel.person.target` = `${GRPC_HOST}:${GRPC_PORT}`
- Calls used: `GetPeopleForMovie` (movie detail), `SearchPeople`, person CRUD and the cast/creator role
  RPCs.
- Deadline: `spring.grpc.client.channel.person.default.deadline` = `${GRPC_DEADLINE:5s}` bounds every
  call, so a silent `person-service` fails with `DEADLINE_EXCEEDED` instead of hanging a GraphQL request.

## Artwork storage

- Files are written to `$UPLOADS_DIR/artworks/<uuid>.<ext>` and served from `/uploads/**` by a resource
  handler in `WebConfig`; only the public URL is stored, and deletes refuse escaping paths.
- Limits: 10 MB, and PNG, JPEG, WEBP, GIF or AVIF. The browser checks first for instant feedback; the
  server check is the one that counts.
- A movie's cover (`artworkUrl`) is kept in step with its artwork rows, so removing the cover promotes
  the next artwork instead of leaving a broken image.
- File deletion happens after the database transaction commits, so a rollback can never leave a row
  pointing at a missing file; the worst case is an unreferenced file left on disk.

## Tests

78 tests in 6 classes (`./gradlew test`).

| File | Tests | Focus |
| --- | --- | --- |
| `movie/MovieServiceTest` | 19 | create/trim, update, delete, search, pagination clamps, sorting, cover sync |
| `artwork/ArtworkServiceTest` | 15 | upload gate (empty, oversize, wrong type), extension mapping, cover promotion, removal |
| `config/UploadStorageTest` | 9 | unique names, byte round-trip, deletes, after-commit timing, path-traversal refusals |
| `grpc/PersonClientTest`, `grpc/PersonClientDeadlineTest` | 4 | in-process gRPC: response and request mapping, error propagation, and the configured deadline |
| `MovieServiceGraphQlIntegrationTest` | 31 | GraphQL end to end via `GraphQlTester` on H2 with a mocked gRPC client |

Integration tests run against H2 in PostgreSQL mode, writing files to `build/test-uploads`.
