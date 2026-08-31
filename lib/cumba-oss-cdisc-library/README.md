# cumba-oss-cdisc-library

CDISC Library REST client and model DTOs — rule packages, SDTM / ADaM /
CDASH / CT model resources, document and QRS endpoints, search. Built
on top of `cumba-oss-web-api`.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-cdisc-library</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.cdisc.library.api.*` — REST client + endpoint classes
- `net.cumba.cdisc.library.api.model.*` — Jackson DTOs (rule, SDTM,
  ADaM, CT, documents, qrs, search)

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-help` | compile | CDT helpers + URI plumbing |
| `cumba-oss-web-api` | compile | HTTP transport, link traversal, caching |

## Notes

The CDISC CORE rules engine loads rule packages through this client. For offline use, callers can also feed pre-downloaded rule JSON
directly to the engine without using the REST endpoint.

See the root [README](../../README.md) for project-wide context.
