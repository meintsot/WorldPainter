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

Plan 0c-3 integrated cloud worlds into WorldPainter's main editor view. From the WorldPainter
repo root:

```
build-and-run-worldpainter.bat        # Windows
# or:
mvn -pl WPGUI exec:exec                # cross-platform
```

WorldPainter opens normally. Use **Cloud → Sign in…** to authenticate, then either:

- **Cloud → Open cloud world…** — pick an existing cloud world (or click "New World…")
- **File → New World** — pick **Cloud** as the Storage mode

Either path opens the cloud world **in WorldPainter's main editor view** with all the
existing brushes, layers, biomes, terrain, and tools panels working against it. Edits
stream to the backend as CRDT ops; remote ops from other connected clients apply silently
in the background.

Run a second WorldPainter instance with a different display name and pick the same world
to see two-client convergence in the full editor experience.

See [MANUAL-TEST.md](./MANUAL-TEST.md) §"Phase 0c-3 — Verification in the main WorldPainter
editor" for the full step-by-step verification procedure.

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

## Known limitations (Phase 0c-3)

These items remain unresolved and are deferred to a future phase:

- **`File → Save` is disabled for cloud worlds** — cloud worlds auto-persist via the op
  stream; a "Download as local file" export is TD-040.
- **Hard-coded layer ids for built-in layers, runtime ids for custom plugin layers** — plugin
  layers' ids aren't stable across sessions (TD-035). Built-in layer ops are fully
  interoperable between clients.
- **Snapshot materialization is terrain-only** — when a tile first loads, only the terrain
  byte field is materialized into the `CloudTile`. Heights, water levels, and layer data
  exist on the backend but aren't yet bridged into the `Tile` representation on load
  (TD-037). New paints work correctly for all field types.
- **Standard-height worlds only** — `CloudStorageBackend.open` builds dimensions with min=0,
  max=256. Tall worlds (max > 256, `tallHeightMap` / `tallWaterLevel`) need wider op fields
  (TD-038).
- **Platform mapping is single-fixed** — cloud worlds open with a hard-coded platform
  (`JAVA_ANVIL`). Per-world platform selection at create time is TD-041.

## Heavy cloud operations (Phase 0c-5)

Three operations run on a dedicated worker subprocess (Phase 0 = `LocalProcessJobRunner`
in the same container; Phase 1 = `HetznerJobRunner` on a separate VM with no code change):

- **Cloud → Export world on cloud…** — server-side Hytale export, downloads result zip.
- **Cloud → Import existing Hytale world…** — server-side Hytale import generates CRDT ops.
- **Cloud → Merge with Hytale world…** — server-side merge of cloud edits onto an uploaded
  Hytale world, downloads merged result zip.

All three are disabled until a cloud world is open. They communicate via:

- `POST /v1/worlds/{id}/jobs` to submit
- `GET /v1/jobs/{id}` to poll
- `GET /v1/jobs/{id}/result-url` for presigned download
- `POST /v1/uploads` for presigned upload (Import, Merge)

See `MANUAL-TEST.md` § Phase 0c-5 for the end-to-end verification procedure.
