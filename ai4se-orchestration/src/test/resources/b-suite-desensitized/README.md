# B-suite desensitized customer stand-in

Synthetic customer repo for pathway V3/V4 sign-off (no secrets, no real customer body).

## Known issue (gold story)

`ConfigService.timeoutMs()` ignores `app.timeout.ms` and returns a hardcoded value.
Customer test: `mvn -q test` must fail until Development fixes Allowed file.
