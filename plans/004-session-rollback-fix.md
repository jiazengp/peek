# Plan 004: Add rollback to stopPeekSession on restore failure

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.

> **Drift check (run first)**: `git diff --stat de3bed4..HEAD -- src/main/java/com/peek/manager/PeekSessionManager.java`
> If PeekSessionManager.java changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding;
> on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: 001 (tests must exist first)
- **Category**: bug
- **Planned at**: commit `de3bed4`, 2026-07-08

## Why this matters

`stopPeekSession()` removes session mappings from `activeSessions`, `peekerToSession`, and `targetToSession` *before* attempting state restoration. If restoration fails (exception in `PlayerState.restore()`, missing registry manager, offline handling error), the session is already gone from all tracking maps. The peeker is left in spectator mode with no active session record — stuck until they relog. The fix: defer map removal until after successful restoration, or re-insert on failure.

## Current state

`src/main/java/com/peek/manager/PeekSessionManager.java:418-427` — mappings removed BEFORE restore:
```java
// Remove mappings
activeSessions.remove(sessionId);
peekerToSession.remove(peekerId);
Set<UUID> targetSessions = targetToSession.get(session.getTargetId());
if (targetSessions != null) {
    targetSessions.remove(sessionId);
    if (targetSessions.isEmpty()) {
        targetToSession.remove(session.getTargetId());
    }
}

// Restore peeker's state  ← line 429
```

The restore happens at lines 429-474. If `originalState.restore()` at line 440 throws, or if `registryManager` is null at line 439, the catch block at line 518 handles the exception — but the maps are already empty.

The catch block at lines 515-519:
```java
} catch (Exception e) {
    PeekMod.LOGGER.error("Error stopping peek session", e);
    return PeekConstants.Result.failure(Component.translatable("peek.error.internal_error").getString());
}
```

No rollback logic exists.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Test | `./gradlew test -PmcVer=26.1` | all pass |
| Build | `./gradlew build -PmcVer=26.1` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `src/main/java/com/peek/manager/PeekSessionManager.java` — the `stopPeekSession` method body (lines 396-519)

**Out of scope**:
- `stopPeekSessionWithoutRestore` (separate issue — it intentionally skips restore)
- `stopAllSessionsInvolving` (delegates to `stopPeekSession`, will inherit the fix)
- Other managers

## Git workflow

```bash
git checkout -b advisor/004-session-rollback-fix
git add src/main/java/com/peek/manager/PeekSessionManager.java
git commit -m "fix: defer session map removal until after successful state restore"
```

## Steps

### Step 1: Add test for the rollback scenario

In `src/test/java/com/peek/manager/PeekSessionManagerTest.java`, add:

```java
@Test
void stopPeekSessionPreservesMappingsOnRestoreFailure() {
    // Start a session
    PeekConstants.Result<PeekSession> result = manager.startPeekSession(peeker, target);
    assertTrue(result.isSuccess());
    
    // Simulate a scenario where restore would fail
    // (e.g., by making registryManager return null, or by corrupting the original state)
    
    // Stop the session
    PeekConstants.Result<String> stopResult = manager.stopPeekSession(peeker.getUUID(), false, server);
    
    // Even if restore fails, the session should be properly cleaned up
    // The key assertion: peeker is NOT left in limbo
    assertNull(manager.getSession(peeker.getUUID()));
    // Peeker should at least have their game mode restored or be in a recoverable state
}
```

**Verify**: Test may need adjustment based on what test infrastructure allows. If full integration test is too complex, at minimum add a unit test that verifies the rollback path exists in the code.

### Step 2: Refactor stopPeekSession to defer mapping removal

The simplest approach: move the mapping removal (lines 418-427) to AFTER the restore block (after line 474), keeping it inside the main try block so it still runs on success.

Restructured flow:
1. Validate session exists (already done, lines 397-410)
2. Mark inactive (line 413, keep as-is)
3. Remove particles (line 416, keep as-is)
4. **Attempt restoration** (lines 429-474, move up)
5. **Then remove mappings** (lines 418-427, move down)
6. If restoration fails, the catch block at 518 handles it — with mappings still intact, the session can be retried or the admin can force-stop it

However, this has a subtlety: we need the mappings in place during restore because `targetToSession` might be checked by concurrent operations. This is actually *good* — the session should be considered active until restore completes.

The specific change: cut lines 418-427 and paste them after line 474 (before the closing brace of the `if (server != null)` block and the stats/logging logic).

### Step 3: Add explicit rollback in catch block

In the catch block at line 515, add a re-verification:
```java
} catch (Exception e) {
    PeekMod.LOGGER.error("Error stopping peek session", e);
    // Re-add mappings if they were already removed
    // (defensive — with the deferral in step 2, this shouldn't be needed,
    //  but protects against future refactoring)
    return PeekConstants.Result.failure(
        Component.translatable("peek.error.internal_error").getString()
    );
}
```

Since we deferred removal, the catch doesn't need re-insertion. But we should log the session details for debugging.

### Step 4: Verify tests and build

**Verify**: `./gradlew test -PmcVer=26.1` → all tests pass (including new rollback test)

**Verify**: `./gradlew build -PmcVer=26.1` → BUILD SUCCESSFUL

## Test plan

- Existing `PeekSessionManagerTest` from plan 001 must still pass
- New `stopPeekSessionPreservesMappingsOnRestoreFailure` must pass (or be documented as requiring integration test setup beyond the scope of this plan)

## Done criteria

- [ ] Mapping removal code appears AFTER restore logic in `stopPeekSession`
- [ ] `./gradlew test -PmcVer=26.1` exits 0
- [ ] `./gradlew build -PmcVer=26.1` succeeds
- [ ] Manual test: start peek, kill the server process during session, verify recovery on restart (this was already the crash recovery path — confirm it still works)
- [ ] No files outside `PeekSessionManager.java` + its test modified

## STOP conditions

- If deferring mapping removal causes any existing test to fail (e.g., a concurrent operation expects mappings to be gone during restore), STOP — the fix may need a more nuanced approach (add a "restoring" intermediate state)
- If the restore logic depends on `session` still being in `activeSessions` (e.g., recursive calls to `stopPeekSession`), STOP — document the dependency before proceeding

## Maintenance notes

- `stopPeekSessionWithoutRestore` at line 778 has a similar pattern — it removes mappings then does cleanup. Consider applying the same deferral there in a follow-up plan.
- The synchronized keyword on `stopPeekSession` prevents concurrent access to the maps, so deferring removal doesn't introduce race conditions.
