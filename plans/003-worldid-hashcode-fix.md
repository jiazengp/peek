# Plan 003: Fix PlayerState.worldId unreliable hashCode guard

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.

> **Drift check (run first)**: `git diff --stat de3bed4..HEAD -- src/main/java/com/peek/data/peek/PlayerState.java`
> If PlayerState.java changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding;
> on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: 001 (characterization tests must exist first)
- **Category**: bug
- **Planned at**: commit `de3bed4`, 2026-07-08

## Why this matters

`PlayerState.capture()` uses a flawed guard to generate the world identifier UUID. The code checks `dimension().identifier().hashCode() != 0` before computing a UUID — but `String.hashCode()` can legitimately return 0 for some strings. When it does, the code falls back to `UUID.randomUUID()`, which will never match the real world UUID during `restorePosition()`. This causes silent cross-world restoration failure: the player ends up in the wrong dimension after their peek session ends. Low probability but data-corrupting when it hits.

## Current state

`src/main/java/com/peek/data/peek/PlayerState.java:54-56`:
```java
UUID worldId = ServerPlayerCompat.getWorld(player).dimension().identifier().hashCode() != 0 ?
    UUID.nameUUIDFromBytes(ServerPlayerCompat.getWorld(player).dimension().identifier().toString().getBytes()) :
    UUID.randomUUID();
```

The `restorePosition()` method at line 155-175 does the same UUID conversion for comparison:
```java
UUID currentWorldId = UUID.nameUUIDFromBytes(
    world.dimension().identifier().toString().getBytes()
);
if (currentWorldId.equals(worldId)) {
    targetWorld = world;
    break;
}
```

If `capture()` fell back to `UUID.randomUUID()`, this comparison will never match — the player stays in whatever world they're in.

The fix is simple: `UUID.nameUUIDFromBytes()` always succeeds — it never throws and never returns null. The `hashCode() != 0` guard is unnecessary and harmful. Remove it.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Test | `./gradlew test -PmcVer=26.1` | all pass incl. new test |
| Build | `./gradlew build -PmcVer=26.1` | BUILD SUCCESSFUL |
| Run | `./gradlew runServer -PmcVer=26.1` | server starts, Peek Mod init OK |

## Scope

**In scope**:
- `src/main/java/com/peek/data/peek/PlayerState.java` — lines 54-56 (capture) and line 161 (restore comparison)

**Out of scope**:
- Any other files
- The `vehicleBubbleTime` dead code (separate issue)
- Changing the UUID generation approach (using ResourceKey directly instead of UUID — separate performance plan)

## Git workflow

```bash
git checkout -b advisor/003-worldid-hashcode-fix
git add src/main/java/com/peek/data/peek/PlayerState.java && git commit -m "fix: remove unreliable hashCode guard in PlayerState.worldId capture"
```

## Steps

### Step 1: Add failing test (if plan 001 didn't cover it)

In `src/test/java/com/peek/data/peek/PlayerStateTest.java`, add:

```java
@Test
void worldIdIsDeterministicForSameWorld() {
    // Given two captures in the same world
    var state1 = PlayerState.capture(player, registryLookup);
    var state2 = PlayerState.capture(player, registryLookup);
    // Then worldIds should be equal (not random)
    assertEquals(state1.worldId(), state2.worldId());
}

@Test  
void worldIdDiffersForDifferentWorlds() {
    // Given captures in different worlds
    // (test infrastructure permitting)
}
```

**Verify**: The first test should FAIL with the current code (because of the hashCode=0 edge case, or at minimum because the logic is suspect). If it already passes, try with a dimension identifier known to have hashCode 0.

### Step 2: Fix the capture method

Replace lines 54-56:
```java
UUID worldId = ServerPlayerCompat.getWorld(player).dimension().identifier().hashCode() != 0 ?
    UUID.nameUUIDFromBytes(ServerPlayerCompat.getWorld(player).dimension().identifier().toString().getBytes()) :
    UUID.randomUUID();
```
with:
```java
UUID worldId = UUID.nameUUIDFromBytes(
    ServerPlayerCompat.getWorld(player).dimension().identifier().toString().getBytes()
);
```

### Step 3: Verify tests pass

**Verify**: `./gradlew test --tests "*PlayerStateTest" -PmcVer=26.1` → all tests pass

### Step 4: Run full build

**Verify**: `./gradlew build -PmcVer=26.1` → BUILD SUCCESSFUL

## Test plan

- Existing `PlayerStateTest` from plan 001 must pass
- New `worldIdIsDeterministicForSameWorld` test must pass
- Manual test: start peek session, teleport target to another dimension, stop session — peeker should return to original dimension

## Done criteria

- [ ] `./gradlew test -PmcVer=26.1` exits 0, all `PlayerStateTest` pass
- [ ] `grep "hashCode" src/main/java/com/peek/data/peek/PlayerState.java` returns no matches
- [ ] `grep "randomUUID" src/main/java/com/peek/data/peek/PlayerState.java` returns no matches (in the worldId context)
- [ ] `./gradlew build -PmcVer=26.1` succeeds
- [ ] No files modified outside `PlayerState.java` + its test

## STOP conditions

- If `PlayerStateTest` cannot create a player in a specific world for the cross-world test, STOP — the cross-world test can be deferred; only the `worldIdIsDeterministic` test is required
- If removing the guard reveals that some dimension identifiers actually fail UUID generation, STOP — report the failure; this would indicate a deeper issue with the identifier format

## Maintenance notes

- This fix makes the worldId computation consistent. In a future optimization (separate plan), consider using `ResourceKey<Level>` directly instead of UUID for world comparison — it would be simpler and faster.
- If Minecraft changes the ResourceLocation.toString() format in a future version, the UUID generation would produce different values for the same world — but since capture and restore happen in the same server session, both sides would use the same format.
