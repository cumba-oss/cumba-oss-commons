# cumba-oss-web-api

Lightweight HATEOAS / JSON-over-HTTP client toolkit: `Link`,
`ApiResource`, `AbstractApiClient`, `JdkHttpTransport`, `FileApiCache`,
and friends. Used by `cumba-oss-cdisc-library` as its REST plumbing.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-web-api</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.web.api.*`

## Dependencies

None on other Cumba OSS modules — pure JDK HTTP + Jackson.

## Notes

Mainly transitive — most embedders won't need it directly, it arrives via
`cumba-oss-cdisc-library`.

See the root [README](../../README.md) for project-wide context.
