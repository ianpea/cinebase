# person-service

Owns people and the roles that connect them to movies: cast members and creators.

It speaks **gRPC only**. There is no REST API and no GraphQL here — `movie-service` calls it, and the
frontend never does. Keeping this data behind its own service is what makes the gRPC hop real rather
than decorative.

## Requirements

- JDK 21
- PostgreSQL 16+
- No local Gradle — the wrapper is included (`./gradlew`)

## Configuration

Everything has a working default, so local development needs no environment variables.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/person_service` | database connection |
| `DB_USERNAME` | `cinebase` | database user |
| `DB_PASSWORD` | `cinebase` | database password |
| `GRPC_PORT` | `9090` | gRPC server port |

## Run it

```bash
./gradlew bootRun     # gRPC on port 9090
./gradlew test        # 73 tests
./gradlew bootJar     # build the runnable jar
```

An HTTP port (8082) is also bound because the web starter is on the classpath, but the service
exposes no HTTP API. In Compose only the gRPC port needs to be reachable from `movie-service`.

## Data ownership

This service owns three tables in the `person_service` database and nothing else:

| Table | Contents |
| --- | --- |
| `people` | name, biography, birth date |
| `movie_cast` | movie id, person id, character name |
| `movie_creator` | movie id, person id, job |

`movieId` is an ordinary number — there is no cross-database foreign key, and this service never reads
the `movies` table. Two facts follow from that, and both are deliberate:

- Adding a role for a movie id that does not exist succeeds here; `movie-service` owns the movie and
  is the only place that can reject it.
- Deleting a person does not delete their roles. The roles stay and are skipped when a movie's cast
  is resolved, rather than cascading a delete across a service boundary.

## gRPC

- Port: `9090` (`GRPC_PORT`)
- Contract: [`../proto/cinebase/person/v1/person-service.proto`](../proto/cinebase/person/v1/person-service.proto)

| Group | RPCs |
| --- | --- |
| People | `GetPerson`, `SearchPeople`, `CreatePerson`, `UpdatePerson`, `DeletePerson` |
| Movie roles (read) | `GetPeopleForMovie` — cast and creators for one movie in a single call |
| Cast | `AddCastMember`, `UpdateCastMember`, `RemoveCastMember` |
| Creators | `AddCreator`, `UpdateCreator`, `RemoveCreator` |

`PersonGrpcService` only translates between protobuf messages and domain objects; `PersonService`,
`CastService` and `CreatorService` hold the rules. Domain failures are mapped to gRPC statuses
(`NOT_FOUND`, `INVALID_ARGUMENT`, `INTERNAL`) in one place, so `movie-service` can present a single
consistent error to the user.

## Tests

```bash
./gradlew test        # 73 tests, 5 classes
```

| File | Tests | Focus |
| --- | --- | --- |
| `person/PersonServiceTest` | 18 | create/trim, update, delete, pagination clamps, search limits |
| `cast/CastServiceTest` | 7 | add, update character name, remove, missing person |
| `creator/CreatorServiceTest` | 6 | add, update job, remove, missing person |
| `grpc/PersonGrpcServiceTest` | 17 | proto ↔ domain mapping, batched lookup, and the full gRPC status contract |
| `PersonServiceGrpcIntegrationTest` | 25 | real gRPC server + H2: person CRUD, case-insensitive search, role add/update/remove, not-found wiring, per-movie role isolation |

Two layers of gRPC testing are used on purpose: an in-process server with mocked services for the
mapping and status contract, and a Spring Boot test against the real server and database for the
behaviour that only appears end to end.
