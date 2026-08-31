# Cumba OSS Commons

Shared infrastructure for the Cumba OSS stack: utility helpers, an HTTP/web API client, a CDISC Library API client, and a configurable launcher.

Licensed under the **GNU Affero General Public License v3.0 only**
(see [`LICENSE`](LICENSE)).

This is the **base layer** — it depends on no other Cumba OSS repository.

## Modules

| Module | Java package | Purpose |
|---|---|---|
| [`cumba-oss-help`](lib/cumba-oss-help/README.md) | `net.cumba.datatable.help` | Cross-cutting helpers: `CDT` string/collection utilities, `AsyncSupport`, `URIHelper`, `StringInterner`. |
| [`cumba-oss-web-api`](lib/cumba-oss-web-api/README.md) | `net.cumba.web.api` | HTTP/REST client plumbing with response caching, used by the CDISC Library client. |
| [`cumba-oss-cdisc-library`](lib/cumba-oss-cdisc-library/README.md) | `net.cumba.cdisc.library` | Client and model for the CDISC Library API (SDTM, ADaM, CDASH, controlled terminology). Depends on `cumba-oss-help` and `cumba-oss-web-api`. |
| [`cumba-oss-bootstrap`](lib/cumba-oss-bootstrap/README.md) | `net.cumba.bootstrap` | Standalone launcher: child-first classloading, a `.conf` file resolved next to the jar, variable expansion. Nothing else here depends on it. |

Each module has its own `README.md` with coordinates and dependency detail.

## The Cumba OSS repositories

```
cumba-oss-commons     help · web-api · cdisc-library · bootstrap
      ▲
cumba-oss-datatable   datatable · impl · cdisc-define · providers · manager-local
      ▲
cumba-oss-formats     sas-utils · datasetjson            (independent leaf)
```

Dependencies run in one direction only. Build order is
`cumba-oss-commons` → `cumba-oss-formats` → `cumba-oss-datatable`.

## Quick start

```bash
mvn -T1C clean install
```

Artifacts are published under groupId `net.cumba` with the module's
artifactId, e.g.:

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-help</artifactId>
    <version>${revision}</version>
</dependency>
```

## Build profiles

| Profile             | Active when               | Purpose                                       |
|---------------------|---------------------------|-----------------------------------------------|
| `PMD`               | unless `-DskipPmd`        | runs PMD at `verify`, **report-only** by default |
| `SpotBugs`          | unless `-DskipSpotbugs`   | runs SpotBugs at `verify`, **report-only** by default |
| `Pitest`            | `-P Pitest` or `-Dpitest.enabled=true` | **opt-in** mutation testing at `verify`, **report-only** by default |
| `ecj`               | `-P ecj`                  | second ECJ compile at `verify`                |
| `ErrPrn`            | `-P ErrPrn`               | Error Prone as a javac plugin                 |
| `spotless-check-mode` | `-Dspotless.check=true` | swaps Spotless from `apply` to `check`        |
| `pitest-fail-on-error` | `-Dpitest.failOnError=true` | promotes per-module `pitest.*.target` to the effective threshold (no-op without `Pitest` active) |
| `spotbugs-module-ignore` | per-module file `spotbugs_ignore.xml` exists | layers per-module SpotBugs filter |

## Static-analysis fail toggles

Every always-on check (Spotless, SpotBugs, PMD, Error Prone) runs
by default but produces a **report only** — they do not block the
build on findings. Pitest is **opt-in** (mutation testing is too slow
for inner-loop builds) and likewise report-only once activated. The
CI gate is opt-in:

| Property                       | Default | When set to `true`                                |
|--------------------------------|---------|---------------------------------------------------|
| `-Dspotbugs.failOnError=true`  | `false` | SpotBugs findings fail the build                  |
| `-Dpmd.failOnViolation=true`   | `false` | PMD findings fail the build                       |
| `-Derrprn.failOnWarning=true`  | `false` | Error Prone findings fail the build (requires `-P ErrPrn`) |
| `-Dspotless.check=true`        | `false` | Spotless switches to `check` mode; unformatted files fail the build (does not rewrite) |
| `-Dpitest.failOnError=true`    | `false` | Pitest mutation/coverage/test-strength thresholds are promoted from each module's `pitest.*.target` and enforced (requires the Pitest profile — see below) |

And the disable / opt-in switches:

| Property                    | Effect                                            |
|-----------------------------|---------------------------------------------------|
| `-DskipPmd`                 | skip the PMD profile entirely                     |
| `-DskipSpotbugs`            | skip the SpotBugs profile entirely                |
| `-Dpitest.enabled=true`     | opt in to the Pitest profile (equivalent to `-P Pitest`) |
| `-Derrprn.extraArgs=...`    | append args to Error Prone, e.g. enable NullAway   |

> **Pitest opt-in:** the `Pitest` profile is dormant by default.
> Activate it with `-P Pitest` (manual profile selection) or
> `-Dpitest.enabled=true` (property activation). `-Dpitest.failOnError=true`
> is a no-op on its own — it only promotes the thresholds inside the
> `pitest-fail-on-error` sub-profile, and without `Pitest` active the
> plugin doesn't run, so no thresholds are evaluated. Always combine,
> e.g. `mvn -P Pitest verify -Dpitest.failOnError=true`.

> **Pitest threshold gotcha:** do **not** pass
> `-Dpitest.mutation.threshold=…` on the command line — same trap as
> `-Djacoco.line.coverage`: a CLI `-D` clobbers every per-module
> override at once. Tune per-module by setting
> `<pitest.mutation.target>` (and `<pitest.coverage.target>`,
> `<pitest.test.strength.target>`) in the module's `pom.xml`, then
> let `-Dpitest.failOnError=true` promote them.

> **No build profile is required.** The reactor is declared at the top
> level of the parent pom, so every command below builds all modules
> with no `-P`. This used to be split across `activeByDefault` `dev` and
> `main` profiles, which silently did not work: Maven drops an
> activeByDefault profile as soon as any other profile activates, and
> `PMD`/`SpotBugs`/`NullAway` self-activate unconditionally — so a plain
> `mvn install` built the parent pom alone, reporting success having
> compiled nothing. If you have `-P main` in a script, drop it; Maven
> will warn that the profile does not exist.

## Continuous integration

Two workflows run the same four-pass ladder against the same gates:

| File | Runs on | Scope |
|------|---------|-------|
| `.gitea/workflows/main.yml` | internal Gitea + act_runner | build, gates, Sonar, Nexus deploy, release |
| `.github/workflows/ci.yml`  | GitHub-hosted runners       | build, gates, Maven Central publish + GitHub Release on tag |

The GitHub workflow is **not** a copy of the Gitea one, and the two cannot
be unified. In particular the artifact actions are pinned to *opposite*
major versions on purpose: Gitea's act_runner implements the GHES-style
artifact API and rejects `@v4`, while on GitHub `@v3` is retired. The
GitHub job also uses `actions/setup-java` rather than the private build
image, resolves everything from Maven Central rather than the internal
mirror, and runs no Sonar, deploy or release step.

Nothing in the pom is CI-specific — both workflows run the same commands
listed above, with no `-P`.

### Releasing

Push the tag to **both** remotes; each platform builds it independently
and cuts its own release from its own jars:

```bash
git tag v1.2.3 && git push gitea v1.2.3 && git push github v1.2.3
```

Releases are not git refs, so nothing syncs them — the Gitea run deploys
to the internal Nexus, while the GitHub run publishes to Maven Central and
cuts the public release. Both derive the version from the tag (`v1.2.3` ->
`1.2.3`), so the jars are identical.

> ⚠ **The Central publish is deliberately not fully automatic.** The
> `deploy-central` profile sets `autoPublish=false`, so a tag uploads a
> *staged* bundle that you must release by hand at central.sonatype.com.
> Publishing is irreversible and versions are immutable — a bad `1.2.3`
> burns `1.2.3` forever — so the staging step exists to be inspected, and
> dropped if it is wrong. The GitHub Release job waits on the publish job,
> so a release is never announced for artifacts that failed to upload.

Central requires four repository secrets: `CENTRAL_USERNAME` and
`CENTRAL_PASSWORD` (tokens generated in the portal, not your login), plus
`GPG_PRIVATE_KEY` (ASCII-armored) and `GPG_PASSPHRASE`.

The two workflow files never collide: Gitea reads `.gitea/workflows` and
falls back to `.github/workflows` only when the former is absent, and
GitHub never looks at `.gitea/`.

## Build commands

```bash
mvn -T1C clean install                            # full build, report-only checks
mvn -T1C test                                     # all tests
mvn -T1C verify -Dspotless.check=true             # CI: verify formatting without rewriting
mvn -T1C verify -Dspotbugs.failOnError=true -Dpmd.failOnViolation=true   # CI: hard gate
mvn -T1C -P Pitest verify                         # opt in to pitest, report-only
mvn -T1C -P Pitest verify -Dpitest.failOnError=true  # CI: pitest + enforce mutation/coverage targets
mvn -T1C verify -DskipPmd -DskipSpotbugs          # quick build, no static analysis (pitest already off)
mvn -T1C initialize sonar:sonar                   # SonarQube (initialize is required
                                                  # so sonar-exclusions.properties loads)
```

Standalone `mvn sonar:sonar` does **not** trigger `initialize`, so the
suppression file never loads — always invoke as
`mvn -T1C initialize sonar:sonar` (or any lifecycle command that
already includes the `initialize` phase, e.g.
`mvn -T1C verify sonar:sonar`).

## Build conventions

- **Java 25** (set via `<java.version>` and `maven.compiler.release`).
- **`<revision>` + `flatten-maven-plugin`** for CI-friendly versioning.
- **Lombok** as compile-time annotation processor; `@CustomLog`
  injects a `java.lang.System.Logger` field named `LOGGER` (see
  `lombok.config`).
- **Strict lint:** `failOnWarning=true` plus `-Xlint:all` in the base
  compiler configuration makes any javac warning a build failure, on
  every build.
- **Spotless** reformats Java sources in-place at `process-sources`
  (before compile) using the Eclipse JDT formatter and
  `eclipse-formatter.xml`. Imports are sorted, unused imports
  removed, trailing whitespace stripped. `mvn install` modifies your
  working tree as a side-effect; CI gates with `-Dspotless.check=true`
  to fail rather than rewrite.
- **SpotBugs** runs at `verify`, layered with
  `spotbugs_project_filter.xml` (always) plus the module's
  `spotbugs_ignore.xml` (auto-activated when present). **Report-only
  by default**; CI flips with `-Dspotbugs.failOnError=true`.
- **Surefire test-CWD isolation.** The forked test JVM's working
  directory is pinned to `${project.build.directory}/test-cwd` (i.e.
  `target/test-cwd/`). A test that resolves a relative path
  (`new File("foo")`, `Files.write(Path.of("out.txt"), …)`, …) lands
  inside `target/` and gets wiped by `mvn clean` instead of polluting
  the repo checkout. Tests that legitimately need the module root or
  the multi-module root read them from system properties Surefire
  exposes per fork: `System.getProperty("projectBasedir")` (the
  module's `${project.basedir}`) and `System.getProperty("repoRoot")`
  (`${maven.multiModuleProjectDirectory}`, i.e. the reactor root).
- **JaCoCo** enforces a per-module line-coverage minimum.
  `<jacoco.line.coverage>` defaults to `0.80` (80%). Override
  per-module by setting the property in the module's pom, or globally
  on the CLI with `-Djacoco.line.coverage=0.0`. Greenfield projects
  typically start at 0 and raise the bar as the test suite matures.
- **Pitest** mutation testing is **opt-in** (`-P Pitest` or
  `-Dpitest.enabled=true`) because mutation analysis is too slow for
  the inner loop. Once active it runs at `verify`, report-only by
  default; pair the opt-in with `-Dpitest.failOnError=true` to
  promote each module's `pitest.mutation.target` /
  `pitest.coverage.target` to the effective thresholds. Incremental
  analysis is enabled via the OSS `io.github.mibimiflo:pitest-history`
  SPI plugin (pitest 1.17+ removed its built-in OSS history reader);
  per-module history is written to
  `~/.pitest-history/<artifactId>/history.bin` under the **home dir**
  (outside any module's `target/` *and* outside the checkout, so neither
  `mvn clean` nor a workspace clean wipes it), and the CI workflow caches
  the directory so warm-cache runs reuse it.
- **License aggregation** via `license-maven-plugin`. Run
  `mvn license:add-third-party` to generate `src/license/THIRD-PARTY.txt`.
  The plugin is wired into `pluginManagement` but no licenseUrl
  rewrites are configured by default — add them as needed.

## Adding a new module

1. Create a directory under `lib/`, named exactly as the artifactId.
2. Add a `pom.xml` with `<parent>` pointing at this root pom.
3. Add the directory to the parent's top-level `<modules>`, **and** as a dependency of
   `coverage/pom.xml` — the aggregate report is built from that
   dependency list, not from the reactor. An enforcer rule in
   `coverage/pom.xml` fails the build if the two lists drift.
4. Add a `<dependency>` entry for it in the parent `<dependencyManagement>`.
5. If it should contribute to a profile's aggregate coverage report,
   add it as a `<dependency>` in the matching `coverage/<profile>/pom.xml`.
