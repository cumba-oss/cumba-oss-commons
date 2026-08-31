# `lib/` — library modules

One sub-directory per library artifact, named exactly as its
artifactId. Library modules produce a plain jar, have no `mainClass`,
and inherit from the project parent pom (`../../pom.xml`).

## Modules

- [`cumba-oss-help`](cumba-oss-help/README.md) — helpers — no dependencies
- [`cumba-oss-web-api`](cumba-oss-web-api/README.md) — HTTP/REST client — no dependencies
- [`cumba-oss-cdisc-library`](cumba-oss-cdisc-library/README.md) — CDISC Library client — depends on help + web-api
- [`cumba-oss-bootstrap`](cumba-oss-bootstrap/README.md) — launcher — no dependencies, nothing depends on it

Modules are listed in the parent pom's `<modules>` in dependency order,
though Maven's reactor derives the real build order itself.

## Adding a new library module

1. Create `lib/<artifact-id>/` with a `pom.xml` whose `<parent>` points
   at `../../pom.xml` and whose `<artifactId>` matches the directory
   name.
2. Add the directory to the parent pom's top-level `<modules>` list.
3. Add a `<dependency>` entry for it in the parent
   `<dependencyManagement>` so consumers need no version.
4. If it should contribute to the aggregate coverage report, list it as
   a dependency in `coverage/pom.xml`. An enforcer rule there fails the
   build if that list and the parent's `<modules>` drift apart.
