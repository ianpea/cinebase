# person-service

Owns people and the roles that connect them to movies: cast members and creators.

It speaks **gRPC only** — no REST and no GraphQL. `movie-service` calls it; the frontend never does.

## Configuration and run

Requires JDK 21 and PostgreSQL 16+ (the Gradle wrapper is included). Every setting below has a working
default, so local development needs no environment variables.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/person_service` | database connection |
| `DB_USERNAME`, `DB_PASSWORD` | `cinebase` | database credentials |
| `GRPC_PORT` | `9090` | gRPC server port |

```bash
./gradlew bootRun     # gRPC on port 9090
./gradlew test        # 84 tests
./gradlew bootJar     # build the runnable jar
```

An HTTP port (8082) is bound because the web starter is on the classpath, but no HTTP API is exposed.

### Docker

Run from the repository root.

```bash
docker compose build person-service      # build the image
docker compose up -d person-service      # start it, along with PostgreSQL
```

See [Building images](../README.md#building-images) to build every service at once.

The image is `cinebase-person-service`. Inside Compose, `movie-service` reaches this service at the
network alias `person-service:9090`, never `localhost`.

## Data ownership

| Table | Contents |
| --- | --- |
| `people` | name, biography, birth date |
| `movie_cast` | movie id, person id, character name |
| `movie_creator` | movie id, person id, job |

Those are the only tables this service owns. `movieId` is an ordinary number: there is no
cross-database foreign key and this service never reads the `movies` table. Movie ids come from
`movie-service`, which owns movies and confirms that a movie exists before it sends a cast or
creator role over gRPC; this service simply stores the id it is given, which keeps it free of any
dependency on movie data.

Deleting a person also deletes their cast and creator roles, in the same transaction: every table
involved belongs to this database, so the cleanup never crosses a service boundary.

## gRPC

- Port: `9090` (`GRPC_PORT`)
- Contract: [`../proto/cinebase/person/v1/person-service.proto`](../proto/cinebase/person/v1/person-service.proto)

| Group | RPCs |
| --- | --- |
| People | `GetPerson`, `SearchPeople`, `CreatePerson`, `UpdatePerson`, `DeletePerson` |
| Movie roles (read) | `GetPeopleForMovie` — cast and creators for one movie in a single call |
| Cast | `AddCastMember`, `UpdateCastMember`, `RemoveCastMember` |
| Creators | `AddCreator`, `UpdateCreator`, `RemoveCreator` |

`PersonGrpcService` translates between protobuf messages and domain objects; `PersonService`,
`CastService` and `CreatorService` hold the rules. Domain failures map to gRPC statuses (`NOT_FOUND`,
`INVALID_ARGUMENT`, `INTERNAL`) so `movie-service` can present one consistent error.

## Tests

84 tests in 5 classes (`./gradlew test`).

| File | Tests | Focus |
| --- | --- | --- |
| `person/PersonServiceTest` | 24 | create/trim, duplicate name + birth date rejection, update, delete order, pagination clamps, search limits, case-insensitive name sorting |
| `cast/CastServiceTest` | 7 | add, update character name, remove, missing person |
| `creator/CreatorServiceTest` | 6 | add, update job, remove, missing person |
| `grpc/PersonGrpcServiceTest` | 17 | proto ↔ domain mapping, batched lookup, and the full gRPC status contract |
| `PersonServiceGrpcIntegrationTest` | 30 | real gRPC server + H2: person CRUD, duplicate identity rejection, case-insensitive search, role add/update/remove, not-found wiring, role cleanup on person deletion, per-movie role isolation |

In-process tests cover gRPC mapping and statuses; the integration test runs the real server on H2.
