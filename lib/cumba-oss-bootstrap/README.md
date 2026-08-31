# `cumba-oss-bootstrap` — configurable launcher

A small, **dependency-free** launcher jar (JDK-only). Instead of baking
the classpath and JVM system properties into a fixed `java -jar`
invocation, the launcher reads a sidecar config file shipped next to the
jar, applies it, and reflectively invokes a target `main`.

What it does, in order:

1. **Locate** the config file (see *Config location* below).
2. **Parse** the INI-style config.
3. Seed the `bootstrap.dir` system property to the config's directory.
4. **Apply** the `[properties]` as JVM system properties, in declaration
   order, resolving `${...}` references.
5. **Build** a classpath from the `[classpath]` entries: interpolate,
   glob-expand, de-duplicate by canonical path.
6. Build an isolated `URLClassLoader` (platform classloader as parent),
   set it as the thread context classloader.
7. Resolve the **target main class** and invoke
   `main(String[])`, passing the launcher's own arguments through.

## Running

```sh
java -jar cumba-oss-bootstrap.jar [args...]      # reads cumba-oss-bootstrap.conf next to the jar
java -Dbootstrap.config=/path/app.conf -jar cumba-oss-bootstrap.jar [args...]
```

## Config location

* If `-Dbootstrap.config=<path>` is set, that file is used.
* Otherwise the launcher takes the jar it was loaded from
  (`.../foo.jar`) and looks for `.../foo.conf` beside it.
* If the launcher runs from an exploded directory (no jar, e.g. an IDE),
  `-Dbootstrap.config` is required.

## File format

INI-style, parsed top-to-bottom. Lines whose first non-blank character
is `#` or `;` are comments; blank lines are ignored. The `[classpath]`
section holds bare path entries (one per line, **not** `key = value`) —
which is why the file is named `.conf` rather than `.ini`.

```ini
[bootstrap]
main-class = com.example.App
# optional; default parent-first
classloader = parent-first

[properties]
app.home   = ${env:APP_HOME:-${bootstrap.dir}}
conf.dir   = ${app.home}/conf
log.file   = ${conf.dir}/app.log
classpath.error.mode = WARN

[classpath]
${app.home}/lib/*.jar
${app.home}/plugins/**.jar
${app.home}/classes
```

### `[bootstrap]`

| Key           | Meaning                                                            |
|---------------|-------------------------------------------------------------------|
| `main-class`  | Fully-qualified target class with a `public static void main`.    |
| `classloader` | `parent-first` (default) or `child-first` (see *Classloader*).    |

If `main-class` is omitted, the launcher falls back to a
`Bootstrap-Main-Class` header in its own jar manifest.

### `[properties]`

Each `key = value` becomes `System.setProperty(key, value)`, applied in
order. Values are interpolated (below). Because they are applied in
order, a later value may reference an earlier key.

### `[classpath]`

One filesystem entry per line, kept in declared order. Relative entries
resolve against the **config file's directory**. Entries are interpolated
then glob-expanded, and the final list is de-duplicated by canonical
path (first occurrence wins) — so the same jar is never added twice.

## Variable interpolation

| Token              | Resolves to                                                          |
|--------------------|----------------------------------------------------------------------|
| `${name}`          | an earlier `[properties]` key, else a system property                |
| `${env:NAME}`      | environment variable `NAME`                                          |
| `${sys:name}`      | system property `name`                                               |
| `${...:-default}`  | the above, using literal `default` if unset **or empty**             |
| `$$`               | a literal `$`                                                        |

A reference that resolves to nothing and has no `:-default` is a hard
error. `bootstrap.dir` is pre-seeded to the config file's directory, so
`${bootstrap.dir}` is always available (and is also visible to the
launched program as a system property).

## Globbing

`*` and `?` match within a single path segment; `**` matches across
directory boundaries (standard `java.nio` glob semantics):

| Pattern           | Matches                                                     |
|-------------------|-------------------------------------------------------------|
| `lib/*.jar`       | jars **directly** in `lib/`                                 |
| `lib/**.jar`      | jars in `lib/` **and** any subdirectory (all depths)        |
| `lib/**/*.jar`    | jars in **sub**directories of `lib/` only (needs a level)   |

Glob expansion does **not** follow symbolic links (standard `java.nio`
`Files.walk` behaviour): jars reachable only through a symlinked
directory under a `**` glob are not descended into.

### `classpath.error.mode`

A system property (settable in `[properties]` or via `-D`) controlling
what happens when a classpath entry does not exist or a glob matches
nothing:

* `WARN` (default) — print a warning to stderr and skip the entry.
* `ERROR` — fail fast.

## Classloader

The launcher builds a `URLClassLoader` over the assembled classpath with
the **platform** classloader (JDK only) as parent.

* `parent-first` (default) — standard delegation; the parent (JDK) is
  consulted first. Correct for almost all deployments.
* `child-first` — the configured classpath is consulted before the
  parent; `java.*`/`javax.*`/`jdk.*`/`sun.*` are always delegated to the
  parent so core classes can never be shadowed. An escape hatch for
  unusual version-override needs.

## Exit behaviour

A launcher-side failure (bad config, unresolved variable, missing
classpath entry under `ERROR`, target class/main not found) prints a
`bootstrap: ...` message to stderr and exits with status `2`. Otherwise
the target program controls its own exit status; exceptions it throws
propagate unchanged.

## Trust model

The config file is **operator-owned and trusted**. Classpath entries
are resolved as written — including absolute paths and `..` segments —
and `${env:...}` / `${sys:...}` interpolation lets the environment feed
values into them. Anything that can edit the `.conf`, or set the
environment variables / system properties it references, can place
arbitrary jars on the classpath, and that code runs in-process. Treat
the config file and the launching environment with the same trust as the
application itself; the launcher applies no sandboxing or path
containment.

`Launcher.launch` also mutates global JVM state (system properties, the
thread context classloader) and does not restore it — it is built for a
one-shot launcher process, not repeated/concurrent use inside a host.

## Building a distribution

The intended packaging is an assembly that bundles this launcher jar,
the application jar and its dependencies, and a sidecar `.conf` named
after the launcher jar, into a runnable zip. No such assembly ships in
this repository — `cumba-oss-bootstrap` is published as a library and
consumed by the distribution build that needs it.
