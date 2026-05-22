# WPCloud — Cloud Client Library for WorldPainter

This module is the client side of the TalePainter cloud architecture. It provides:

- **CRDT op generation and application** (`crdt/`, `tile/`) — same HLC + LWW semantics as the
  backend `backend-shared` module.
- **WebSocket transport** (`transport/`) — Tyrus-based client speaking the backend's protocol.
- **REST auth client** (`auth/`) — login + Java `Preferences`-backed token persistence.
- **`CloudTileProvider`** (`tile/`) — the integration class that wires everything together.
  Consumers (`WPCloudDemo` in this module; the full WorldPainter UI in Phase 0c-2) interact
  with this class.
- **`WPCloudDemo`** (`examples/`) — a standalone Swing paint app for end-to-end verification.

## Building

```
cd WorldPainter   # the reactor root inside the repo
mvn -DskipTests -pl WPCloud -am package
```

This produces `WPCloud/target/WPCloud-1.2.0-SNAPSHOT-with-deps.jar` — a fat jar runnable as the
demo app.

## Dependencies

WPCloud depends on `com.talepainter:client-protocol:0.1.0-SNAPSHOT`, which is built and
installed from the [talepainter-backend repo](https://github.com/meintsot/talepainter-backend).
Before building WPCloud:

```
cd talepainter-backend
mvn -DskipTests -pl client-protocol install
```

This installs the JAR to your local Maven cache (`~/.m2/repository/com/talepainter/...`),
where WPCloud picks it up.

> **Note:** Phase 0c-1 uses this local-Maven approach. Phase 1+ will publish the protocol
> artifact to a real repository (tracked as TD-029).

## Running the demo

Prerequisite: backend stack up.

```
# Terminal A:
cd talepainter-backend/infra/compose
docker compose --env-file .env up -d --build

# Terminal B:
cd WorldPainter/WorldPainter
java -jar WPCloud/target/WPCloud-1.2.0-SNAPSHOT-with-deps.jar
```

Optional CLI args:

```
java -jar WPCloud/target/WPCloud-1.2.0-SNAPSHOT-with-deps.jar [backend-http-url]
# Default: http://localhost:8080
```

See [MANUAL-TEST.md](./MANUAL-TEST.md) for the two-window verification procedure.

## Running tests

### Unit tests (no backend needed)

```
cd WorldPainter
mvn -pl WPCloud -am test
```

Expected: 30 active tests pass (4 integration tests skipped via `@Disabled`), BUILD SUCCESS.

### Integration tests (backend must be running)

Four tests are tagged `@Tag("integration")` and `@Disabled` by default:

- `TyrusWebSocketClientTest` — confirms WS handshake/heartbeat round-trip.
- `AuthClientIntegrationTest` — confirms login returns a NoOp token.
- `SingleClientRoundTripTest` — confirms a write persists across provider close/reopen.
- `TwoClientConvergenceTest` — confirms two providers converge on concurrent writes
  (Phase 0c-1 acceptance test).

To run them:

```
# Backend stack up (see above)
cd WorldPainter
mvn -pl WPCloud -am test \
    -Dtest=TwoClientConvergenceTest \
    -Djunit.jupiter.conditions.deactivate=org.junit.*
```

## Code organization

```
src/main/java/org/pepsoft/worldpainter/cloud/
├── auth/         AuthClient (REST login) + CredentialsStore (Preferences) + Session record
├── crdt/         ClientHlcClock, OpGenerator, OpQueue (with coalescing)
├── tile/         LocalTile, TileSnapshotDecoder, OpApplicator, TileCache, CloudTileProvider
├── transport/    ProtocolCodec, WebSocketClient interface + TyrusWebSocketClient impl
└── examples/     WPCloudDemo (the Swing demo)
```

## Known limitations (Phase 0c-1)

These are deferred to Plan 0c-2 or later:

- **No reconnection** — if the WebSocket drops, the provider is dead (TD-030). Restart the app.
- **No integration with WorldPainter's existing UI** — this module is self-contained. The
  Swing demo is the only consumer in Phase 0c-1. The full WorldPainter integration (Cloud Mode
  in NewWorldDialog, cloud world picker, sign-in dialog, sync status indicator, Dimension's
  `TileProvider` injection, Tile mutation listener) lands in Plan 0c-2.
- **Single tile per window** — the demo paints only tile (0, 0). Pan/zoom isn't supported.
- **LOD pyramid not used** — only LOD 0 (full detail) tiles are fetched (TD-008).
- **Tokens stored in plain text** via Java `Preferences`. Acceptable for NoOp auth; will need
  OS-secure storage when real OAuth lands in Phase 1 (TD-031).
