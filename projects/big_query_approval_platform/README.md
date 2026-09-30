# big_query_approval_platform

A Spring Boot backend where users request one of a fixed set of queries against
the `phone_activation` BigQuery dataset, a human reviewer approves or rejects
each request, and approved requests are run on BigQuery.

Status: in progress. The file skeleton exists; the code does not yet.

## Data

- Project `query-approval-platform-test`, dataset `phone_activation` (location `US`)
- Star schema: `fact_phone_activation` plus `dim_country`, `dim_date` and `dim_phone`

See the root [README](../../README.md#querying-the-bigquery-dataset) for querying
it with the `bq` CLI.

## Workflow

```text
submit ──► PENDING ──reviewer: yes──► APPROVED ──run on BigQuery──► DEPLOYED  (rows + CSV path)
                  │                                              └► FAILED    (error message)
                  └──reviewer: no + reason──► REJECTED  (reason returned to requester)
```

Any transition not shown is invalid (for example, deciding a request that is
already `REJECTED`).

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /requests` `{queryType, params, requestedBy}` | Check the input format (malformed input gets a `400` and never reaches a reviewer), store the request as `PENDING`, and return its id and the SQL it will run |
| `GET /requests?status=PENDING` | The reviewer's queue |
| `POST /requests/{id}/decision` `{approved, reason, reviewer}` | `approved: false` requires a `reason` and sets `REJECTED`; `approved: true` sets `APPROVED`, runs the query, then sets `DEPLOYED` or `FAILED` |
| `GET /requests/{id}` | The requester checks the status, reject reason or result |

Error responses:

| Status | When |
|---|---|
| `400` | Unknown query type, missing or malformed parameters, rejection without a reason |
| `404` | Unknown request id |
| `409` | Decision on a request that isn't `PENDING`, or a reviewer deciding their own request |

## Query types

Each type is a fixed SQL template over the star schema:

| Type | Parameters | Returns |
|---|---|---|
| `ACTIVATION_STATUS` | `imei` | Whether the phone is activated, and if so the country and date |
| `ACTIVATIONS_BY_COUNTRY` | `from`, `to` | Activation count per country in the date range |
| `ACTIVATIONS_OVER_TIME` | `from`, `to` | Activation count per month |

Parameter formats:

- `imei`: exactly 15 digits, kept as a string
- `from`, `to`: ISO dates (`yyyy-MM-dd`), with `from` on or before `to`

## Design rules

- User input never becomes part of the SQL text. Templates are fixed and values
  are passed as BigQuery query parameters (`WHERE imei = @imei`), so SQL
  injection isn't possible.
- Treat IMEI as a string: validate it as 15 digits but never parse it as a
  number, because some IMEIs start with `0`.
- A reviewer can't approve their own request.
- Deploying saves the result as a CSV in `output_data/`, in the same format as
  the `bq --format=csv` output.
- Requests are kept in memory at first, behind a store interface so a database
  can replace it later.
- The service calls BigQuery through a `QueryRunner` interface, so tests use a
  fake runner and never touch BigQuery.
- The Java BigQuery client uses Application Default Credentials, which are
  separate from the `bq` CLI's login: run `gcloud auth application-default login` once.

## Layout

```text
src/main/java/approval/
  ApprovalApplication.java      Spring Boot entry point
  domain/                       RequestStatus, QueryRequest, QueryType,
                                ParamValidator, ValidationException  (no Spring)
  store/                        RequestStore interface, InMemoryRequestStore
  service/                      ApprovalService, QueryRunner interface, QueryResult
  web/                          RequestController, request/response DTOs,
                                ApiExceptionHandler (exceptions -> HTTP status)
  bigquery/                     BigQueryRunner (real QueryRunner), CsvWriter
src/test/java/approval/         one test per class above, plus FakeQueryRunner
output_data/                    CSV results (gitignored)
```

## Build order

1. **Skeleton:** `pom.xml` (import the Spring Boot BOM in the root POM, since the
   root POM is already the parent), `ApprovalApplication`, add the module to the
   root POM. Done when `mvn -pl projects/big_query_approval_platform spring-boot:run` starts.
2. **Domain:** statuses and state transitions on `QueryRequest`; invalid
   transitions throw.
3. **Query types and validation:** templates, required parameters, and parameter
   checks in `ParamValidator`.
4. **Store:** thread-safe in-memory implementation.
5. **Service:** submit, queue, get, and decide, including the self-approval rule,
   tested with `FakeQueryRunner`.
6. **HTTP:** controller, DTOs and exception mapping, tested with MockMvc.
7. **BigQuery:** `BigQueryRunner` with named parameters, and `CsvWriter`.
8. **End to end:** use curl to run through approval, rejection and self-approval.
