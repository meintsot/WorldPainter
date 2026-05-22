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

## Running the cloud client

Prerequisite: backend stack up.

```
cd talepainter-backend/infra/compose
docker compose --env-file .env up -d --build
```

### Option A — Via the WorldPainter batch file (recommended for users)

Plan 0c-2 added a **Cloud** menu to WorldPainter's main window. From the WorldPainter repo
root:

```
build-and-run-worldpainter.bat        # Windows
# or:
mvn -pl WPGUI exec:exec                # cross-platform
```

WorldPainter opens normally. Use **Cloud → Sign in…** to authenticate (Phase 0 NoOp auth
just claims a display name), then **Cloud → Open cloud world…** to pick or create a world.
The chosen world opens in a dedicated cloud editor window where you can paint cells.

Run a second WorldPainter instance with a different display name and pick the same world
to see two-client convergence.

See [MANUAL-TEST.md](./MANUAL-TEST.md) §"Phase 0c-2 — Verification via WorldPainter's batch
file" for the full step-by-step verification procedure.

### Option B — Standalone demo jar (useful for developer testing)

The `WPCloudDemo` standalone jar from Plan 0c-1 is still available and useful when you want
to test the cloud client without launching the full WorldPainter app:

```
cd WorldPainter
java -jar WPCloud/target/WPCloud-1.2.0-SNAPSHOT-with-deps.jar [backend-http-url]
# Default backend: http://localhost:8080
```

See [MANUAL-TEST.md](./MANUAL-TEST.md) §"Manual two-client convergence verification" for the
standalone-jar procedure.

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
