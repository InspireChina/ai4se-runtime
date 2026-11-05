# First Delivery Sample

Config service for a small service.

## Known issue

`ConfigService.timeoutMs()` currently returns a hardcoded value and ignores
`app.timeout.ms` in `application.properties`.
