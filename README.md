# learning-java

A Java workspace for small to medium learning projects, built with Maven.

## Structure

A Maven multi-module build. The root `pom.xml` is the parent/aggregator; each
learning project under `projects/` is a module with the standard Maven layout.

```text
pom.xml                       parent POM: shared versions, module list
projects/
  sqlvalidator/
    pom.xml                   module POM
    src/main/java/            production code   (package `sqlvalidator`)
    src/test/java/            JUnit 5 tests
  datacoalesce/               not a module yet (no code)
  big_query_approval_platform/
    output_data/              local BigQuery query results (gitignored)
```

- `projects/sqlvalidator` — rule-based validator for read-only SQL strings
- `projects/datacoalesce` — data coalesce project (empty)
- `projects/big_query_approval_platform` — synthetic phone-activation data in
  BigQuery (not a Maven module yet; see [Querying the BigQuery dataset](#querying-the-bigquery-dataset)),
  and the planned [query approval workflow](#query-approval-workflow-planned)

## Requirements

- JDK 25+ (bytecode target is set to 25)
- Maven 3.9+

## Common commands

Run from the repo root:

```bash
mvn test                                  # build + run every module's tests
mvn -pl projects/sqlvalidator test        # just one module
mvn -q -pl projects/sqlvalidator compile exec:java   # run the sqlvalidator demo
```

## Adding a project

1. `mkdir -p projects/<name>/src/main/java/<name>` and `.../src/test/java/<name>`
2. Add a `projects/<name>/pom.xml` that inherits from the root POM
   (copy `projects/sqlvalidator/pom.xml` as a starting point)
3. Add `<module>projects/<name></module>` to the root `pom.xml`

## Querying the BigQuery dataset

The data for `projects/big_query_approval_platform` lives in BigQuery, not in
this repo:

- Project `query-approval-platform-test`, dataset `phone_activation` (location `US`)
- Star schema: `fact_phone_activation` plus `dim_country`, `dim_date` and `dim_phone`

This needs the Google Cloud SDK (`bq`) and an account with access to the
project (`gcloud auth login`, then `gcloud config set project query-approval-platform-test`).

To save a query result as CSV in `output_data/`, run from
`projects/big_query_approval_platform`:

```bash
mkdir -p output_data
bq --quiet query --use_legacy_sql=false --location=US --format=csv \
  'SELECT * FROM `query-approval-platform-test.phone_activation.fact_phone_activation` ORDER BY activation_key LIMIT 10' \
  > output_data/fact_phone_activation_sample.csv
```

- `--format=csv` prints the result as CSV, and `>` writes it to the file
- `--quiet` keeps bq's "Waiting on job…" progress lines out of the file
- `bq query` returns at most 100 rows by default; add `--max_rows=N` for more
- Add `ORDER BY` so a `LIMIT` returns the same rows each run
- NULLs are written as empty cells and booleans as `true`/`false`

Both `data/` and `output_data/` in this project are gitignored.

## Query approval workflow (planned)

Not built yet: this is the design for a Spring Boot backend in
`projects/big_query_approval_platform`. Users request one of a fixed set of
queries against the `phone_activation` dataset, a human reviewer approves or
rejects each request, and approved requests are run on BigQuery.

```text
submit ──► PENDING ──reviewer: yes──► APPROVED ──run on BigQuery──► DEPLOYED  (rows + CSV path)
                  │                                              └► FAILED    (error message)
                  └──reviewer: no + reason──► REJECTED  (reason returned to requester)
```

### Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /requests` `{queryType, params, requestedBy}` | Check the input format (malformed input gets a `400` and never reaches a reviewer), store the request as `PENDING`, and return its id and the SQL it will run |
| `GET /requests?status=PENDING` | The reviewer's queue |
| `POST /requests/{id}/decision` `{approved, reason, reviewer}` | `approved: false` requires a `reason` and sets `REJECTED`; `approved: true` sets `APPROVED`, runs the query, then sets `DEPLOYED` or `FAILED` |
| `GET /requests/{id}` | The requester checks the status, reject reason or result |

### Query types

Each type is a fixed SQL template over the star schema:

| Type | Parameters | Returns |
|---|---|---|
| `ACTIVATION_STATUS` | `imei` | Whether the phone is activated, and if so the country and date |
| `ACTIVATIONS_BY_COUNTRY` | `from`, `to` | Activation count per country in the date range |
| `ACTIVATIONS_OVER_TIME` | `from`, `to` | Activation count per month |

### Design rules

- User input never becomes part of the SQL text. Templates are fixed and values
  are passed as BigQuery query parameters (`WHERE imei = @imei`), so SQL
  injection isn't possible.
- Treat IMEI as a string: validate it as 15 digits but never parse it as a
  number, because some IMEIs start with `0`.
- A reviewer can't approve their own request.
- Deploying saves the result as a CSV in `output_data/`, like the `bq` command above.
- Requests are kept in memory at first, behind a store interface so a database
  can replace it later.
- The Java BigQuery client uses Application Default Credentials, which are
  separate from the `bq` CLI's login: run `gcloud auth application-default login` once.
