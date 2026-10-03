# cumba-oss-help

Cross-cutting utility helpers used by every other Cumba OSS module:
`CDT` (null-safe string/collection helpers), `AsyncSupport` (a small
`CompletableFuture` adapter that surfaces checked `IOException`s),
`URIHelper` (URI manipulation — fragment replacement, file-name
extraction), `StringInterner` (a sharded, weak-reference `String`
canonicaliser with `String.intern()` semantics, but without the JVM's
single globally synchronised native table), `GenericListView` (an
unmodifiable, zero-copy `List` view whose elements are computed on
access from an `IntFunction`) and `SecureTempFiles` (temp files and
directories restricted to the current user on POSIX filesystems; the
platform default elsewhere). The most foundational module in the
project.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-help</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.help.*`

## Dependencies

None on other Cumba OSS modules (this is the bottom of the dependency
graph).

## Notes

See the root [README](../../README.md) for project-wide context.
