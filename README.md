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
```

- `projects/sqlvalidator` — rule-based validator for read-only SQL strings
- `projects/datacoalesce` — data coalesce project (empty)

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
