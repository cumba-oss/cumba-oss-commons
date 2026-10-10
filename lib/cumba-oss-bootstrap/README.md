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
6. Resolve the **target main class**.
7. Apply the optional **user overlay** (see *User overlay* below) — only
   now, so nothing in it can change the classpath or the main class.
8. Build an isolated `URLClassLoader` (platform classloader as parent),
   set it as the thread context classloader, and invoke
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

INI-style, read as UTF-8 and parsed top-to-bottom; a UTF-8 byte-order mark at
the start of the file is ignored. Lines whose first non-blank character
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
| `user-config` | A candidate location of the user overlay. **Repeatable**, in order. |
| `reserved`    | Comma-separated keys the user overlay may not set. Repeatable, merged; an empty value or a key with whitespace in it (a missing comma) is an error. |
| `single-instance` | Opts the distribution into the single-instance gate (see *Single instance*); the value is the instance scope, a directory. An empty value is an error. Absent: the gate does not exist for this distribution. |

If `main-class` is omitted, the launcher falls back to a
`Bootstrap-Main-Class` header in its own jar manifest. A `main-class`
that is present but blank — including one that interpolates to nothing —
counts as omitted, so `main-class = ${env:CUMBA_MAIN:-}` means "use the
manifest's".

### `[properties]`

Each `key = value` becomes `System.setProperty(key, value)`, applied in
order. Values are interpolated (below). Because they are applied in
order, a later value may reference an earlier key.

### `[classpath]`

One filesystem entry per line, kept in declared order. Relative entries
resolve against the **config file's directory**. Entries are interpolated
then glob-expanded, and the final list is de-duplicated by canonical
path (first occurrence wins) — so the same jar is never added twice.

An entry that *interpolates to nothing* — `${env:EXTRA_LIBS}` with the
variable exported but empty — is **not** a relative path meaning "here".
It is routed through `classpath.error.mode` like a missing entry, because
resolving it would silently place the config directory itself on the
classpath in place of the jars the operator meant to add.

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

## User overlay

An installed application's `.conf` is often not the user's to edit: it sits
inside a signed macOS `.app`, under a read-only `Program Files`, and a
re-install replaces it. The bundled config can therefore name a second,
**user-owned** file whose `[properties]` are applied **after** its own and
**win** over them:

```ini
[bootstrap]
main-class  = com.example.App
user-config = ${sys:user.home}/.example/app.conf
reserved    = app.home, app.lib.dir
```

**Locating it.**

* `user-config` may be repeated; the **first candidate that is an existing
  regular file** is used, and none is the normal case (no overlay).
* A candidate is resolved **leniently**: a `${...}` reference that is unset
  or empty **and has no usable default** skips that candidate (so
  `${env:APPDATA}/...` is simply passed over on Linux and macOS). A reference
  whose default resolves is not skipped:
  `${env:XDG_CONFIG_HOME:-${sys:user.home}/.config}` yields `~/.config`. A
  candidate that resolves to an empty string is skipped too.
  Syntax errors (an unterminated `${`, an empty name) still fail the launch —
  also after a reference that is unset. The one exception is inside a
  `:-default` that is never used: defaults are resolved lazily, as in the
  shell, so a typo there fails only where the default is needed.
* Candidates see the environment, the system properties as they were
  **before** the bundled `[properties]` were applied, and `${bootstrap.dir}`
  — never the bundled keys. A relative candidate resolves against
  `bootstrap.dir`. ⚠ So a `user.home` set in either file does not move the
  overlay; `${sys:user.home}` is the JVM's own.
* `-Dbootstrap.user.config=<path>` replaces the candidate list with one file,
  which must exist and be a regular file (relative: against the working
  directory). It is honoured
  **only when the config declares at least one `user-config`** — a config
  without candidates has no overlay at all, whatever the command line says.
* A blank `-Dbootstrap.user.config` counts as unset.

**What it may contain.** `[properties]` only — any other section is an error.
The overlay is applied after the classpath, the error mode and the main class
are fixed, so it cannot change what code runs. These keys are **reserved** and
rejected in the overlay (error naming the file and key): `bootstrap.dir`,
`bootstrap.config`, `bootstrap.user.config`, `bootstrap.user.config.applied`,
`bootstrap.user.config.candidates`, `bootstrap.error.dialog`,
`classpath.error.mode` — plus whatever the bundled config lists in
`[bootstrap] reserved`. A key the launcher has already consumed but that is
not reserved may be set; it only becomes a system property the application
sees, with no launcher effect.

**Precedence.**

* An overlay value is set **unconditionally**: a bare `key = value` beats the
  bundled value **and** a `-D` on the command line.
* `${sys:...}` in the overlay resolves against the snapshot taken at the start
  of the launch — the JVM's own properties and the command line, not the
  bundled values. So `key = ${sys:key:-value}` lets a `-D` win for that key.
* ⚠ Write `${sys:key:-value}`, never `${key:-value}`: a bare `${key}` sees the
  bundled keys first, so it resolves to the bundled value and the entry
  silently does nothing.
* ⚠ Environment variables reach an overlay entry only through `${env:...}` in
  that entry: `key = ${env:APP_KEY:-${sys:key:-value}}`. An environment
  default baked into the *bundled* value is overridden like any other value.
* **Values, not inputs:** overriding a key does not re-derive bundled keys
  that were computed from it — they are already resolved strings.

**Published for the application** (only when the config declares
`user-config`; otherwise both are cleared): `bootstrap.user.config.applied` —
the absolute path of the overlay applied (unset if none);
`bootstrap.user.config.candidates` — the resolved candidate paths (the
override alone when it is in effect), joined with the platform path separator,
so the application can log where it looked. It is empty when every candidate
was skipped, and meant for a log line: a path containing the separator makes
the list ambiguous. Both names (and `bootstrap.user.config`) are public
constants on `Launcher` and part of its contract: an application loaded by the
launcher cannot see the launcher's classes at run time, so it spells them
itself and should pin them equal in a build that has both on its class path.

**Errors** in the overlay name the overlay's path; parse errors also the line
(resolution errors do not — entries carry no line numbers).

**Scope of the guarantee.** The overlay cannot change what code runs. That is
a statement about the *user file*, not about the local user: a `-D` in
`JAVA_TOOL_OPTIONS` still reaches every `${sys:...}` in the bundled config, as
it always has.

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
directory under a `**` glob are not descended into. A dangling symlink
*is* matched and lands on the classpath as an entry that resolves to
nothing (harmless — `URLClassLoader` ignores it).

Matches are not filtered to regular files: a **directory** whose name fits
the pattern is added like any other classpath entry. The directory being
walked is never added to itself.

Glob entries are portable. On Windows both `/` and `\` separate segments
in a `[classpath]` line and may be mixed freely (`${app.home}/lib/*.jar`
works with `app.home = C:\app`); the entry is split as a *string* at its
first glob-bearing segment, so no glob character is ever handed to the
Windows path parser, which rejects `*` and `?` outright. Matching is
case-insensitive on Windows and case-sensitive on UNIX — the platform's
own `java.nio` behaviour.

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
  parent; the reserved `java.*`/`javax.*`/`jdk.*`/`sun.*` namespaces are
  always delegated to the parent, so nothing in them can be shadowed. An
  escape hatch for unusual version-override needs.

  ⚠ Other packages the platform classloader also exports — `org.w3c.dom`,
  `org.xml.sax`, `com.sun.*` — are **not** reserved and *can* be shadowed
  under `child-first`. That is the point of the mode (a legacy
  `xml-apis.jar` is the classic case), but a class shadowed on one side of
  a JDK API boundary and not the other produces a `ClassCastException` at
  first use, so shadow them deliberately or not at all.

## Exit behaviour

A launcher-side failure (bad config or user overlay, unresolved variable, missing
classpath entry under `ERROR`, target class/main not found) prints a
`bootstrap: ...` message to stderr and exits with status `2`. Otherwise
the target program controls its own exit status; exceptions it throws
propagate unchanged.

### Error dialog (opt-in)

A launcher with no console — Launch4j's `gui` header, a jpackage application —
cannot show the `bootstrap: ...` line. Such a launcher sets
`-Dbootstrap.error.dialog=true`, and the same message is then also shown in a
dialog (when a display is available). It is a system property, not a config
key, because the failure to report is often that the config itself cannot be
read or parsed. Without it — the default, and what console and CLI launchers
use — only stderr is written. The exit status is `2` either way.

## Single instance

A distribution whose config names `[bootstrap] single-instance = <scope>` runs
**one instance per user and per scope** (the scope is the user's settings
directory, e.g. `${sys:user.home}/.cumbaDataBrowser`), regardless of the
installed version: a second start hands its files to the running primary and
exits. The gate runs between the user overlay and the class loader - the class
path is fixed, nothing of the application is loaded yet, and the switch below
may come from the user's overlay. Without the key nothing of this exists: no
file, no lock, no socket class and no foreign-function class is touched (this
module's `NoKeyClassLoadTest` pins that in a forked JVM with
`-Xlog:class+load`), which is what keeps `cumba-corej-cli` and
`cumba-corej-rest` unaffected. Whatever the gate throws unexpectedly (a Windows
without Unix domain sockets, a scope that is no path) is logged and the start
runs as a separate instance (`EXTRA:error`): the gate never stops a start.

**Switch.** `[properties] cumba.single.instance = ${sys:cumba.single.instance:-true}`
in the bundled config; the user overlay may set it to `false`. The argument
`--new-instance` starts a separate instance for one start (it is passed on to
the application untouched). Either publishes `bootstrap.instance.outcome=OFF`.

**Lock.** `<scope>/instance/instance.lock`, a `FileChannel.tryLock` on byte 0,
held in a static field for the JVM's life and released by the operating system
on any exit, a crash included. The lock is the truth; the endpoint file beside
it is only a pointer. `instance/` is created owner-only (`rwx------` where the
file system has POSIX modes; Windows inherits the profile's ACL) and the mode is
re-applied on every start. A file system that cannot lock, or a refused
directory, means a separate instance (`EXTRA:no-lock-support`, `EXTRA:dir-check`).

**Primary.** The lock is free: the previous run's `endpoint` is deleted at once
(a failed delete - Windows, a file held open - is logged and ignored; the
application replaces the file atomically), `bootstrap.instance.dir` (the
instance directory), `bootstrap.instance.locked=true` and
`bootstrap.instance.outcome=PRIMARY` are published, and the launcher continues
to the application, which listens. The application writes the endpoint:
`socket=<path>`, `token=<64 hex>`, `version=1` - one fresh socket name
`s-<16 hex>` per start - or `listener=none` + `version=1` when it cannot listen.

**Client.** The lock is held. The paths are every argument not starting with
`--` that names an existing file or directory, resolved against the client's
working directory (none = "come to the front"); a path holding a line break
(`\n` or `\r`: legal on Linux and macOS, but the frame splits on them), more
than 256 paths or one over 32 KiB cannot be handed off, so this start opens
them itself (`path-newline`, `too-many-paths`, `path-too-long`). These limits
bound a hand-off only: they are checked after the lock, and a primary opens
whatever it was given. Then, every 100 ms, the endpoint is re-read, the
socket's directory is verified when it is not the instance directory, and a
connection is attempted. On a missing endpoint or a refused socket the attempt
also tries the lock: free means the old primary died before it listened, and
this start is the primary. While the lock is held and **no endpoint exists**
the primary is alive (the operating system releases a dead process's lock)
and still starting - class loading, a virus scanner on a cold start - so the
client waits up to **30 s** for it (`no-endpoint`); an endpoint that does not
answer (a refused socket, a stale token) is given **10 s** (`timeout`).
`listener=none` (the primary cannot listen) is believed when it is read twice
in a row, 100 ms apart (`no-listener`): a new primary deletes the previous
run's endpoint right after taking the lock, so a first reading may be stale.
After the hello back the client closes its own splash screen, grants the
primary the foreground (Windows: `AllowSetForegroundWindow` through the foreign
function API; the `BOOL` and `GetLastError` are logged), sends its paths and
reads replies: `BUSY STARTING` heartbeats are waited for up to **60 s** in
total, `BUSY QUEUED` (a modal dialog in the primary: the user's time) without a
cap, each reply within **5 s** of the last; `ACCEPTED` = handed off, the JVM
exits 0 without starting the application; `DENIED <reason>` (the primary
refused the `OPEN`) = a separate instance at once; `CLOSING` (the primary is
exiting) or EOF (it died) = wait up to **30 s** for the lock, then become the
primary. A different token appearing in the endpoint during that wait means a
third start took the lock first: the client hands off to it instead, at most
**3** rounds. Any expiry or refusal falls through to a normal start without
lock or listener (`bootstrap.instance.outcome=EXTRA:<reason>`): worst case two
windows, never a lost file. The reasons: `no-lock-support`, `dir-check`,
`no-endpoint`, `timeout`, `start-cap`, `denied-version`, `denied-limit`,
`denied-request`, `denied-failed` (any `denied-<reason>` the primary sends), `no-listener`,
`rounds`, `protocol`, `too-many-paths`, `path-too-long`, `path-newline`,
`interrupted`, `error`.

A known gap: when the previous run's endpoint cannot be deleted (Windows,
another process holds the file open without delete sharing), clients read it
until the application replaces it - a refused socket (10 s, then `timeout`) or
a stale `listener=none` read twice (`no-listener`); either way two windows,
never a lost file.

**Wire protocol v1** over the Unix domain socket: each frame is a 4-byte
big-endian length followed by UTF-8 text, lines split on `\n`. The length is
checked against the cap (8 MiB + 64 KiB: 256 paths of 32 KiB) *before* a buffer
is allocated.

| from | frame | meaning |
|---|---|---|
| client | `CUMBA-INSTANCE 1\n<token>` | hello: protocol version + the endpoint's token |
| server | `CUMBA-INSTANCE 1\n<pid>` | hello back: the version it speaks, its pid (for the foreground grant) |
| server | `DENIED\n<reason>` then close | instead of the hello back: `token` (compared in constant time; the client re-reads the endpoint and retries inside its window), `version` (the client goes EXTRA), `limit` (too many concurrent connections; EXTRA) |
| client | `OPEN\n<n>\n<path 1>\n...<path n>` | absolute paths, `n <= 256`, each at most 32 KiB, none holding `\n` or `\r` |
| server | `BUSY\n<state>` (0..n times) then `ACCEPTED` or `CLOSING` | `STARTING` (the primary is building its window) or `QUEUED` (a modal dialog); a missing or unknown state counts as `STARTING` |
| server | `DENIED\n<reason>` then close | instead of the replies above (after `BUSY` heartbeats too), for an `OPEN` the server refuses: `request` (malformed, over the limits) or `failed` (the primary could not take the files over - its open path threw); the client goes EXTRA (`denied-<reason>`) at once, without a lock wait |

The constants are spelled in this jar and in the application's server half -
the halves share a protocol, not types - and the distribution's build pins the
two spellings equal.

**Security.** The threat model is other users on the same host. The socket and
the endpoint live in the owner-only `instance/`; the hello carries a 256-bit
random token per primary start, read from the owner-only endpoint file; where
the application falls back to a socket directory under a shared location
(Linux: `$XDG_RUNTIME_DIR`, else `java.io.tmpdir`) the client verifies, before
connecting, that the directory is not a symbolic link, is owned by the current
user and is exactly `rwx------`. The current user is taken from the file
system - the owner of the instance directory - never from `user.name`, which a
`-D` can set to anything and a directory-service name need not round-trip. A
peer-credential check (`SO_PEERCRED`, plan SI2 (c)) is **not implemented**: the
owner-only directories keep other users from the socket, and the token is the
check that holds on all three operating systems (Windows has no peer
credentials). The server only *opens files as the user would*; nothing is
executed. Every blocking connect, write and read is guarded
by a close watchdog (a Unix-domain `SocketChannel` has no `SO_TIMEOUT`): a daemon
thread closes the channel when the deadline passes.

**Published for the application.** `bootstrap.instance.outcome` on every gated
start (`PRIMARY`, `EXTRA:<reason>`, `OFF`), `bootstrap.instance.dir` and
`bootstrap.instance.locked` on PRIMARY only. All three are reserved: the user
overlay cannot set them. An application started without the launcher (an IDE)
sees none of them and may take the lock itself; one that sees an outcome must
never open the lock file - closing a second channel on it could release the
launcher's lock.

**Testing.** Every seam (clock, lock, directory checks, platform, foreground
grant, watchdog scheduler) is injected, so every branch and every time-out runs
on a Linux build host on a fake clock; the real lock is tested against a forked
holder process, the client against a stand-in v1 server over a real socket, and
five forked launchers at once against one scope prove one primary and four
hand-offs. The one class outside the module's coverage and mutation floors is
the branch-free Windows binding `WindowsForegroundGrant` (the pom states the
policy).

## Known limitations

* `java.class.path` is **not** updated to the assembled classpath. Code in
  the target program that scans that property, or forks a JVM "with the
  current classpath", sees only the launcher jar.

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

The **user overlay** is user-owned and trusted the same way for what it can
do — set system properties the application reads — but it cannot reach the
classpath, the main class or the launcher's own keys (see *User overlay*).

`Launcher.launch` also mutates global JVM state (system properties, the
thread context classloader) and does not restore it — it is built for a
one-shot launcher process, not repeated/concurrent use inside a host.

## Building a distribution

The intended packaging is an assembly that bundles this launcher jar,
the application jar and its dependencies, and a sidecar `.conf` named
after the launcher jar, into a runnable zip. No such assembly ships in
this repository — `cumba-oss-bootstrap` is published as a library and
consumed by the distribution build that needs it.
