# WPCloud Manual Two-Client Convergence Verification

This procedure visually proves end-to-end CRDT collaboration works. Useful for demoing Phase 0c-1
and as a sanity check before shipping.

## Prerequisites

1. Backend stack running:
   ```
   cd talepainter-backend/infra/compose
   cp .env.example .env   # if you haven't already
   docker compose --env-file .env up -d --build
   sleep 30
   ./smoke-test.sh     # expect: ✅ Smoke test passed.
   ```
2. WPCloud fat jar built:
   ```
   cd WorldPainter/WorldPainter
   mvn -DskipTests -pl WPCloud -am package
   ```

## Procedure

### Window A (Alice)

In Terminal 1:

```
cd WorldPainter/WorldPainter
java -jar WPCloud/target/WPCloud-1.2.0-SNAPSHOT-with-deps.jar
```

- At the "Display name" prompt, enter **Alice**.
- At the world picker, choose **+ New World...** and name it **ManualTest**.
- The 128×128 canvas appears, fully white.

### Window B (Bob)

In Terminal 2 (same machine):

```
cd WorldPainter/WorldPainter
java -jar WPCloud/target/WPCloud-1.2.0-SNAPSHOT-with-deps.jar
```

- At the "Display name" prompt, enter **Bob**.
- At the world picker, **choose ManualTest** from the dropdown (do not pick "+ New World..." or you'll get a different world).
- A second 128×128 canvas appears, also fully white.

### Convergence checks

**Check 1 — Alice's writes appear in Bob's window:**
- In Window A, left-click around cells (5, 5) through (10, 10). They turn red.
- Within ~500 ms, the same cells turn red in Window B.

**Check 2 — Bob's writes appear in Alice's window:**
- In Window B, right-click once on the canvas to advance the brush to a different color (orange/yellow/etc.).
- Left-click around cells (20, 20) through (25, 25).
- Within ~500 ms, those cells appear in Alice's window with Bob's color.

**Check 3 — Same-cell concurrent write:**
- Both users left-click on cell (50, 50) at roughly the same time.
- Both windows briefly disagree (each shows their own color locally) but within ~500 ms BOTH converge to the same color (whichever HLC tie-break resolved to).

**Check 4 — Persistence across restart:**
- Close Window A entirely.
- Reopen Window A as Alice on the same **ManualTest** world.
- The canvas shows the converged state from the previous session, including Bob's edits.

## Pass criteria

- All 4 checks pass.
- No visible lag for local paints (under 50 ms cursor → cell color).
- Window B's view of Alice's edits arrives within 500 ms.
- After restart, the world state persists.

## What this proves

End-to-end Phase 0c-1 architecture:
- WebSocket transport works (Tyrus client ↔ Quarkus endpoint)
- CRDT ops are correctly generated, broadcast, and applied
- HLC LWW resolution converges concurrent writes
- Snapshots + post-snapshot ops correctly reconstruct state on reopen
- Two clients in different JVMs against a real backend behave as designed

---

## Phase 0c-2 — Verification via WorldPainter's batch file

This procedure exercises the cloud menu integration. Same prerequisites as the standalone
demo (backend stack must be running).

### Prerequisites

1. Backend up:
   ```
   cd talepainter-backend/infra/compose
   docker compose --env-file .env up -d --build
   ```
2. WorldPainter built:
   ```
   cd WorldPainter
   mvn -DskipTests -pl WPGUI -am install
   ```

### Procedure

**Window A (Alice):**

1. Run `build-and-run-worldpainter.bat` (from the WorldPainter repo root, or the
   appropriate location per BUILDING.md).
2. WorldPainter opens normally. Verify a **Cloud** menu appears between **Tools** and
   **Help**.
3. Click **Cloud → Sign in…**.
4. Enter display name `Alice`, click **Sign in**. The dialog closes; the menu's
   "Sign in…" item is now disabled and "Sign out" + "Open cloud world…" are enabled.
5. Click **Cloud → Open cloud world…**. A dialog lists existing worlds (likely empty
   on first run).
6. Click **New World…**, name it `PhaseZero`, click OK. The dialog closes; a new
   `WorldPainter Cloud — PhaseZero` window opens. After a brief "Connecting…" splash,
   the 128×128 painting canvas appears, fully white.

**Window B (Bob):**

1. Run `build-and-run-worldpainter.bat` again in a second terminal.
2. Click **Cloud → Sign in…**, enter `Bob`, click Sign in.
3. Click **Cloud → Open cloud world…**. The `PhaseZero` world should appear in the list.
4. Select it and click **Open**. A second cloud editor window appears, also fully white.

**Convergence:**

1. In Window A's cloud editor, left-click around cells (5,5)–(10,10). They turn red.
2. Within ~500 ms, the same cells turn red in Window B's cloud editor.
3. In Window B, right-click once on the canvas (cycles brush color), then left-click around
   cells (20,20)–(25,25). Those cells appear in Alice's editor with Bob's color.
4. Both users left-click the same cell at the same time. After ~500 ms, both editors show
   the same converged color (HLC tie-break by node id).
5. Close Alice's editor window via the X button. Reopen via **Cloud → Open cloud world…
   → PhaseZero → Open**. The cells you painted are still there.

### Pass criteria

- Cloud menu appears in the WorldPainter menu bar.
- Sign-in dialog works; saved session survives WorldPainter restart (close WorldPainter
  entirely, relaunch, **Cloud** menu shows Sign Out enabled — no need to sign in again).
- Two cloud editor windows can paint the same world concurrently and converge.
- No regressions: open a local `.world` file via **File → Open**; existing functionality
  (brushes, layers, save) still works.

### What this proves (Phase 0 acceptance)

- The full architecture works through the regular WorldPainter entry point.
- A user can go from launching the bat to collaborative editing without a separate jar.
- Existing local-file editing is unaffected by the cloud integration.

---

## Phase 0c-3 — Verification in the main WorldPainter editor

This procedure exercises the deep cloud integration: cloud worlds open in WorldPainter's main
editor view, with the real brushes / layers / biomes panels operating on cloud tiles.

### Prerequisites

1. Backend up: `docker compose --env-file .env up -d --build` (from
   `talepainter-backend/infra/compose`).
2. WorldPainter rebuilt: `mvn -DskipTests -pl WPGUI -am install` (from `WorldPainter/`).

### Procedure

**Window A (Alice):**

1. Launch via `build-and-run-worldpainter.bat`.
2. **Cloud → Sign in…** → "Alice" → Sign in.
3. **Cloud → Open cloud world…** → pick an existing world OR click "New World…" and name it
   "Phase0c3" → Open.
4. **The world opens in the MAIN editor view** (not in a separate window). Brushes panel,
   Terrain panel, Layers panel, the map view — all active.
5. Pick the **Pencil** tool (top-left). Pick **Sand** in the Terrain panel.
6. Click on the map → a single cell turns yellow (Sand). It persists.

**Window B (Bob):**

1. Launch via `build-and-run-worldpainter.bat` in a second terminal.
2. Sign in as "Bob", open the same "Phase0c3" world.
3. Bob sees the cell Alice painted (yellow Sand).

**Convergence:**

1. Alice paints a stripe of Sand cells.
2. Within ~500 ms Bob's editor shows the same stripe.
3. Bob picks a different terrain (e.g., Stone) and paints a crossing stripe.
4. Alice sees Bob's stripe.
5. Both users click the same cell. After ~500 ms both editors agree on the winning terrain
   (HLC tie-break).
6. Pick the Frost layer (or any bit layer). Paint with it. Verify it propagates between
   instances.
7. Close Alice's WorldPainter entirely. Reopen, re-sign-in (or auto-restore), reopen
   "Phase0c3". The painted state is preserved.

### Pass criteria

- Cloud worlds open in the main editor (not a separate window).
- Brushes (Pencil, Spray Paint, etc.) work against cloud worlds.
- Terrain changes propagate between instances within ~500 ms.
- Bit layer changes (e.g., Frost) propagate between instances.
- File → Save is gracefully blocked for cloud worlds (info dialog).
- Local file editing (File → Open → some.world) still works (no regression).

### What this proves (Phase 0c-3 acceptance)

- `Tile.MutationListener` integration via `CloudTile` subclass works: brushes paint, ops emit.
- `RemoteOpContext` thread-local correctly suppresses op-emission loops on inbound ops.
- `CloudDimension` lazy-loading works: tiles are fetched as the user pans / brushes touch them.
- The full editor experience is identical for local and cloud worlds (with documented
  exceptions: no local save, single-platform Hytale-or-Java-Anvil mapping in Phase 0).
