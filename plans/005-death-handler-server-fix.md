# Plan 005: Pass server context in death handler session cleanup

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.

> **Drift check (run first)**: `git diff --stat de3bed4..HEAD -- src/main/java/com/peek/PeekMod.java src/main/java/com/peek/manager/PeekSessionManager.java`
> If either file changed since this plan was written, compare the
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

When a player dies while peeking or being peeked, the `AFTER_DEATH` event handler calls `stopAllSessionsInvolving(player.getUUID())` — without passing the `MinecraftServer` reference. Compare with `DISCONNECT` handler which passes `server`. The no-arg overload eventually calls `getCurrentServer()` internally, which may return `null` in some edge cases during death processing. If it does, the offline save path tries to save state — but without a server reference, PlayerDataAPI's offline save may also fail. The fix is trivial: pass the server from the event context.

## Current state

`src/main/java/com/peek/PeekMod.java:143-170` — two adjacent event handlers:

**DISCONNECT** (lines 143-155) — correctly passes server:
```java
ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
    ServerPlayer player = handler.getPlayer();
    try {
        ParticleEffectManager.cleanupPlayerParticles(player.getUUID());
        ManagerRegistry.getInstance().getManager(PeekSessionManager.class)
            .stopAllSessionsInvolving(player.getUUID(), server);  // ← server passed
        ...
    }
});
```

**AFTER_DEATH** (lines 158-170) — missing server:
```java
ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
    if (entity instanceof ServerPlayer player) {
        try {
            ParticleEffectManager.cleanupPlayerParticles(player.getUUID());
            ManagerRegistry.getInstance().getManager(PeekSessionManager.class)
                .stopAllSessionsInvolving(player.getUUID());  // ← NO server passed
            ...
        }
    });
```

The `AFTER_DEATH` callback signature is `(LivingEntity entity, DamageSource damageSource)`. It does not provide a `MinecraftServer` parameter. To get the server, use `ServerPlayerCompat.getServer(player)`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Test | `./gradlew test -PmcVer=26.1` | all pass |
| Build | `./gradlew build -PmcVer=26.1` | BUILD SUCCESSFUL |

## Scope

**In scope**:
- `src/main/java/com/peek/PeekMod.java` — line 165, the death event handler

**Out of scope**:
- Any other event handlers
- `stopAllSessionsInvolving` method signature changes

## Git workflow

```bash
git checkout -b advisor/005-death-handler-server-fix
git add src/main/java/com/peek/PeekMod.java
git commit -m "fix: pass server context in death handler session cleanup"
```

## Steps

### Step 1: Verify the current inconsistency

**Verify**: `grep -n "stopAllSessionsInvolving" src/main/java/com/peek/PeekMod.java`

Expected output:
```
150:    .stopAllSessionsInvolving(player.getUUID(), server);  // disconnect: has server
165:    .stopAllSessionsInvolving(player.getUUID());           // death: no server
```

### Step 2: Fix the death handler

Change line 165 from:
```java
ManagerRegistry.getInstance().getManager(PeekSessionManager.class).stopAllSessionsInvolving(player.getUUID());
```
to:
```java
ManagerRegistry.getInstance().getManager(PeekSessionManager.class).stopAllSessionsInvolving(player.getUUID(), ServerPlayerCompat.getServer(player));
```

Add the import if not already present (it should be — `ServerPlayerCompat` is already imported in PeekMod.java).

### Step 3: Verify tests and build

**Verify**: `./gradlew test -PmcVer=26.1` → all tests pass

**Verify**: `./gradlew build -PmcVer=26.1` → BUILD SUCCESSFUL

### Step 4: Manual verification

Start a dev server, join as two players, start a peek session, kill the peeker — verify the server log shows proper session cleanup with server context (no "server is null" warnings).

## Test plan

If plan 001 created `PeekSessionManagerTest`, extend it:
```java
@Test
void deathHandlerUsesServerContext() {
    // Verify that stopAllSessionsInvolving is called with a non-null server
    // This may require mocking or integration test infrastructure
}
```

If full integration test isn't feasible, the manual verification in step 4 suffices.

## Done criteria

- [ ] Death handler at `PeekMod.java:165` passes server via `ServerPlayerCompat.getServer(player)`
- [ ] `grep "stopAllSessionsInvolving" src/main/java/com/peek/PeekMod.java` shows both calls passing server
- [ ] `./gradlew build -PmcVer=26.1` succeeds
- [ ] No files outside `PeekMod.java` modified

## STOP conditions

- If `ServerPlayerCompat.getServer(player)` returns null during death events (should not happen — player entity exists and is in a world), STOP and investigate; the death event might fire after entity removal, requiring a different approach (e.g., caching the server reference at mod init)

## Maintenance notes

- When adding new event handlers that call `stopAllSessionsInvolving`, always pass the server context. Consider adding an `@Deprecated` annotation on the no-arg overload to discourage future misuse.
- The `ServerLivingEntityEvents.AFTER_DEATH` event fires per entity. If multiple peekers die simultaneously (e.g., explosion), each triggers independently — the synchronized `stopPeekSession` handles this correctly.
