# Networking module validation

This directory contains committed learning deliverables, not generated build output:

- `openapi.yaml`: the completed OpenAPI 3.1 contract;
- `postman/*.json`: a Postman 2.1 collection and a non-secret placeholder environment.

## Contract and Retrofit comparison

The upstream training baseline intentionally names the `GET /users` query parameter `pageSize`,
while DummyJSON and the OpenAPI contract use `limit`. This project already used Retrofit
`@Query("limit")`, so no production correction was necessary. The regression test
`RetrofitUserRemoteDataSourceTest` verifies the complete-directory request path is
`/users?limit=0`.

The Android client uses these operations:

| Operation | Retrofit request | Authentication |
| --- | --- | --- |
| List users | `GET /users?limit=0` | DummyJSON permits anonymous access; the app sends a bearer token when signed in |
| Load account | `GET /users/{id}` | DummyJSON permits anonymous access; the app sends a bearer token when signed in |
| Sign in | `POST /auth/login` | No authentication; returns access and refresh tokens |

The app persists only the authenticated user ID. Access tokens stay in memory and the exported
Postman environment contains no credentials or token values.

## HTTP inspection scenarios

Use the debug application ID `com.example.userlistapp.debug`. Only the debug manifest references
the Network Security Configuration that trusts user-installed CAs. The release manifest has no
custom trust configuration and therefore retains the Android platform default.

### Network Inspector

1. Sign in and open User List.
2. In Android Studio, select **App Inspection > Network Inspector** and the debug process.
3. Pull to refresh and inspect `GET https://dummyjson.com/users?limit=0`.
4. Confirm status 200, `Accept: application/json`, an optional bearer header, response size, and
   request timing.

### Charles

Install and trust the Charles root certificate on the emulator, enable SSL proxying for
`dummyjson.com:443`, and keep the app on the User List route with cached users visible.

| Scenario | Charles action | Expected app result |
| --- | --- | --- |
| Delayed response | Throttle or add a breakpoint delay | Cached users remain visible and refresh remains active until completion |
| HTTP 500 | Rewrite the response status to 500 | Loading ends, cached users remain, an error snackbar appears, retry remains available |
| Malformed body | Rewrite the body to invalid JSON | Loading ends, cached users remain, an invalid-data snackbar appears, retry remains available |

Automated regression coverage exercises network, HTTP 500, and malformed-body failures without
mutating the cached snapshot. `UserListViewModelTest` verifies that loading terminates and retry
remains possible.

## WebSocket validation

The endpoint is `wss://ws.postman-echo.com/raw`. The User List route displays the connection state,
the most recently received message, and a button that sends:

```json
{"type":"echo","message":"Hello from GD User List App"}
```

The connection is requested only while the route lifecycle is at least `STARTED`, and is closed on
`ON_STOP` or route disposal. Duplicate `connect` calls are ignored. Unexpected closure retries at
1, 2, and 4 seconds, then enters `Failed`; leaving and re-entering the route starts a fresh policy.
The client intentionally does not keep a socket in the background. Background server-initiated
delivery would require FCM instead because Android Doze and App Standby do not guarantee a
persistent socket.

Unit tests cover connection states, sending and receiving messages, closure, duplicate prevention,
and the bounded reconnection policy.

## Recorded result

Validation performed on 2026-07-30:

| Check | Result |
| --- | --- |
| OpenAPI YAML parsing and required paths | Pass (SnakeYAML 2.4) |
| Postman collection/environment JSON parsing | Pass (Node.js JSON parser) |
| Retrofit request construction | Pass (`/users?limit=0`) |
| HTTP/cache/loading regression tests | Pass |
| WebSocket state/exchange/closure/duplicate/reconnect unit tests | Pass |
| JVM unit tests (`app` and `settings`) | Pass |
| Android lint | Pass |
| Debug APK assembly | Pass |
| Android test source compilation (`app` and `settings`) | Pass |
| Screenshot tests | Pass (4/4) |
| Settings connected accessibility test | Pass (1/1) |
| App connected tests | 17/18 tests completed in the long run; the new WebSocket UI and route lifecycle tests also passed separately. The existing Sign In IME test crashed the API 33 AVD instrumentation process while the system low-memory killer was active |
| Debug/release certificate trust manifests | Pass: debug contains the custom config; release contains no `networkSecurityConfig` |
| Live Postman Echo WebSocket | Pass: connected, sent the documented JSON, received and displayed the exact echo |

Manual-tool limitations:

- Postman and Charles are not installed in the validation environment, so collection import,
  Postman WebSocket verification, and Charles rewrite/throttle evidence remain manual.
- Android Studio Network Inspector evidence was not captured. The successful DummyJSON login,
  users request, and WebSocket echo were verified in the running debug app.
- External OpenAPI validators were not allowed to receive the workspace file and downloading a
  third-party CLI for execution was blocked. The YAML parses locally and its structure was checked,
  but it should still be opened once in Swagger Editor before the module review.
- Disabling emulator Wi-Fi did not interrupt its virtual Ethernet connection, so live
  connection-loss UI was not reproduced. The bounded 1/2/4-second reconnect behavior is covered by
  deterministic unit tests.
