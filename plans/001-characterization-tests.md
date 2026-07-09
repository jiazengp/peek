# Plan 001: Establish characterization test baseline

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.

> **Drift check (run first)**: `git diff --stat de3bed4..HEAD -- src/test/ src/main/java/com/peek/data/peek/PlayerState.java src/main/java/com/peek/manager/PeekSessionManager.java src/main/java/com/peek/manager/PeekRequestManager.java`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: LOW
- **Depends on**: none
- **Category**: tests
- **Planned at**: commit `de3bed4`, 2026-07-08

## Why this matters

This mod has 12,179 lines of production Java with zero tests. The core promise — player state capture and restoration during peek sessions — has no automated verification. Any bug fix (including plans 003, 004, 005 in this batch) is a blind change without characterization tests proving the current behavior before modification. This plan adds smoke tests for the 3 most critical paths: `PlayerState` round-trip, `PeekSessionManager` start→stop lifecycle, and `PeekRequestManager` send→accept flow.

## Current state

The build already has test infrastructure configured:
- `build.gradle:179-182` declares `fabric-loader-junit` and `fabric-api` as test dependencies
- JUnit 5 is available via Fabric's test framework
- `src/test/` directory does not yet exist

Key files that need tests:
- `src/main/java/com/peek/data/peek/PlayerState.java` — capture/restore round-trip (lines 51-175)
- `src/main/java/com/peek/manager/PeekSessionManager.java` — session lifecycle (lines 70-400)
- `src/main/java/com/peek/manager/PeekRequestManager.java` — request lifecycle

Repo conventions (from `AGENTS.md`):
- Manager pattern via `ManagerRegistry.getInstance().getManager(Xxx.class)`
- `PeekConstants.Result<T>` for success/failure returns
- Compatibility layer in `utils/compat/` — test code should use compat methods too
- Tick system via `TickTaskManager` — tests should not depend on real ticks

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Build | `./gradlew build -PmcVer=26.1` | BUILD SUCCESSFUL |
| Compile | `./gradlew compileJava -PmcVer=26.1` | exit 0 |
| Test | `./gradlew test -PmcVer=26.1` | exit 0, all pass |
| Single test | `./gradlew test --tests "*PlayerStateTest" -PmcVer=26.1` | exit 0 |

## Scope

**In scope**:
- `src/test/java/com/peek/data/peek/PlayerStateTest.java` (create)
- `src/test/java/com/peek/manager/PeekSessionManagerTest.java` (create)
- `src/test/java/com/peek/manager/PeekRequestManagerTest.java` (create)

**Out of scope**:
- Full test coverage — this plan is characterization tests only (prove current behavior, don't test every edge case)
- Any changes to production code — tests only
- Config, UI, particle effects testing

## Git workflow

```bash
git checkout -b advisor/001-characterization-tests
# Commit per test file
git add src/test/ && git commit -m "test: add characterization tests for PlayerState"
```

Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Create test directory and verify build setup

```bash
mkdir -p src/test/java/com/peek/data/peek
mkdir -p src/test/java/com/peek/manager
```

**Verify**: `./gradlew test -PmcVer=26.1` → exits 0 (with 0 tests, should show "no tests found" or similar)

### Step 2: Write `PlayerStateTest` — capture/restore round-trip

Create `src/test/java/com/peek/data/peek/PlayerStateTest.java`:

Test the capture→restore round-trip for a mock ServerPlayer. Fabric's `fabric-loader-junit` provides `MinecraftServer` in tests — use `@GameTest` or basic JUnit with a server fixture.

Test cases:
1. **captureCapturesCorrectPosition**: Create a player at (10, 64, 20), capture state, assert position fields match
2. **captureCapturesGameMode**: Set player to CREATIVE, capture, assert gameMode == CREATIVE
3. **captureCapturesEffects**: Add a speed effect to player, capture, assert effects list is non-empty
4. **restoreRestoresGameMode**: Capture SURVIVAL state, change player to SPECTATOR, restore, assert gameMode == SURVIVAL
5. **restoreClearsThenReappliesEffects**: Add effect before capture, capture, clear effects, restore, assert effect is back

**Verify**: `./gradlew test --tests "*PlayerStateTest" -PmcVer=26.1` → all 5 tests pass

### Step 3: Write `PeekSessionManagerTest` — session start→stop lifecycle

Create `src/test/java/com/peek/manager/PeekSessionManagerTest.java`:

This is tricky because `PeekSessionManager.startPeekSession()` requires real `ServerPlayer` instances. Use Fabric's test infrastructure to get player entities.

Test cases:
1. **startPeekSessionCreatesActiveSession**: Start session between two players, assert session is in activeSessions
2. **stopPeekSessionRemovesSession**: Start then stop, assert session removed from activeSessions
3. **stopPeekSessionRestoresPeekerState**: Start with peeker in SURVIVAL (session sets them to SPECTATOR), stop, assert peeker gameMode is SURVIVAL again
4. **peekerToTargetMapsCorrectly**: Start session, assert peekerToSession and targetToSession maps are consistent

**Verify**: `./gradlew test --tests "*PeekSessionManagerTest" -PmcVer=26.1` → all 4 tests pass

### Step 4: Write `PeekRequestManagerTest` — request lifecycle

Create `src/test/java/com/peek/manager/PeekRequestManagerTest.java`:

Test cases:
1. **sendRequestCreatesPendingRequest**: Player A sends request to B, assert request exists with correct requester/target
2. **acceptRequestRemovesRequest**: Send then accept, assert request no longer pending
3. **denyRequestRemovesRequest**: Send then deny, assert request no longer pending
4. **duplicateRequestFails**: Send same request twice, assert second call returns failure

**Verify**: `./gradlew test --tests "*PeekRequestManagerTest" -PmcVer=26.1` → all 4 tests pass

### Step 5: Run full test suite

**Verify**: `./gradlew test -PmcVer=26.1` → BUILD SUCCESSFUL, all 13 tests pass

## Test plan

The tests written in this plan ARE the test plan. Future plans (003, 004, 005) add tests for specific bug fixes. After all 5 plans are done, run `./gradlew test -PmcVer=26.1` to confirm full suite passes.

## Done criteria

- [ ] `./gradlew test -PmcVer=26.1` exits 0
- [ ] At least 10 tests exist and pass across the 3 test files
- [ ] PlayerStateTest covers capture and restore
- [ ] PeekSessionManagerTest covers start, stop, and state restoration
- [ ] PeekRequestManagerTest covers send, accept, deny
- [ ] No production code modified (`git diff --stat -- src/main/` is empty)

## STOP conditions

- If Fabric test infrastructure cannot provide mock ServerPlayer instances, STOP — this approach needs reconsideration (a different test strategy, or skip PeekSessionManagerTest and focus on PlayerState + PeekRequest)
- If `./gradlew test` fails to compile with test dependencies, STOP — the build.gradle test configuration may be incomplete
- If any test takes more than 30 seconds (hanging on server startup), STOP — Fabric tests may need different configuration

## Maintenance notes

- These are characterization tests — they document current behavior, not necessarily correct behavior. When fixing bugs in plans 003-005, update the corresponding tests to assert the new correct behavior.
- The Fabric test fixture setup may need adjustment if fabric-loader-junit API changes. The key dependency is `net.fabricmc:fabric-loader-junit` in build.gradle.
- If mock ServerPlayer proves impossible, consider using a full integration test that starts a real Minecraft server (heavier but Fabric-supported via `@GameTest`).
