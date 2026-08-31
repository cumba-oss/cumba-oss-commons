# cumba-oss-help

Cross-cutting utility helpers used by every other Cumba OSS module:
`CDT` (null-safe string/collection helpers), `AsyncSupport` (a small
`CompletableFuture` adapter that surfaces checked `IOException`s), and
`URIHelper` (URI manipulation — fragment replacement, file-name
extraction). The most foundational module in the project.

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
