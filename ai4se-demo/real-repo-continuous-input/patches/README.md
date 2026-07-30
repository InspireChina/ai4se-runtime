# First Delivery Sample

Config service for a small service.

## Configuration

| Key | Meaning | Default |
|-----|---------|---------|
| `app.timeout.ms` | Request timeout in milliseconds | `30` |
| `app.name` | Service display name | n/a |

`ConfigService` implements `TimeoutSource` and reads `app.timeout.ms` from `application.properties`.
