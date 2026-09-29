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
  BigQuery (not a Maven module; see [Querying the BigQuery dataset](#querying-the-bigquery-dataset))

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
