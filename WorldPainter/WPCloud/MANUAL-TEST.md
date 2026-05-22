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
