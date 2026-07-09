# Plan 002: Update AGENTS.md for Minecraft 26.x

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.

> **Drift check (run first)**: `git diff --stat de3bed4..HEAD -- AGENTS.md version_properties/ build.gradle`
> If AGENTS.md or version-related files changed since this plan was written,
> compare the "Current state" excerpts against the live code before proceeding;
> on a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: dx
- **Planned at**: commit `de3bed4`, 2026-07-08

## Why this matters

`AGENTS.md` is the primary onboarding document for both human contributors and AI agents working on this repo. It currently claims the project supports "Minecraft 1.21.1 through 1.21.6" and references Java 21 — but the `26.X` branch exclusively builds for Minecraft 26.1+ with Java 25. Build examples use `-PmcVer=1.21.4` which doesn't exist. A new contributor or agent following the docs will waste time on non-existent build targets and outdated configuration. This is a docs-only fix with no runtime risk.

## Current state

`AGENTS.md` contains 6 references to Minecraft 1.21.x:

- Line 7: "Minecraft 1.21.1 through 1.21.6"
- Line 12: "Build for the default version (1.21.6)"
- Line 15: `./gradlew build -PmcVer=1.21.4`
- Line 31: "MC_VER=1214 for conditional compilation"
- Line 37: "// Code for 1.21.5+"
- Line 40: "// Code for 1.21.4 and earlier"

Also outdated:
- Line 30: mentions "yarn mappings" — no longer used (project uses `noIntermediateMappings()`)
- Lines 124-127: "Uses Java 21" — should be Java 25

Current facts (from `version_properties/` and `build.gradle`):
- 3 active versions: 26.1, 26.1.2, 26.2
- Java 25
- Fabric Loader 0.18.4+ (min 0.18.0)
- No Yarn mappings (Mojang mappings directly)

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Build | `./gradlew build -PmcVer=26.1` | BUILD SUCCESSFUL |
| List versions | `./gradlew listVersions` | Shows 26.2, 26.1.2, 26.1 |
| Check refs | `grep -c "1\.21" AGENTS.md` | Should return 0 after fix |

## Scope

**In scope**:
- `AGENTS.md` — update version references, build commands, preprocessor examples
- `CLAUDE.md` — create as symlink to `AGENTS.md`
- `.gitignore` — update line 43 reference

**Out of scope**:
- Any source code changes
- `README.md` (doesn't exist in this repo)
- `fabric.mod.json`

## Git workflow

```bash
git checkout -b advisor/002-agents-md-update
git add AGENTS.md CLAUDE.md .gitignore && git commit -m "docs: update AGENTS.md for Minecraft 26.x, add CLAUDE.md symlink"
```

## Steps

### Step 1: Update "Build System & Multi-Version Support" section

Replace the opening paragraph (line 7):
```
This project uses a sophisticated multi-version build system supporting Minecraft 1.21.1 through 1.21.6:
```
with:
```
This project uses a sophisticated multi-version build system supporting Minecraft 26.1+:
```

Update build command examples (lines 11-18):
- Line 12: `# Build for the default version (1.21.6)` → `# Build for the default version (26.1)`
- Line 15: `./gradlew build -PmcVer=1.21.4` → `./gradlew build -PmcVer=26.2`

### Step 2: Update "Version Configuration System" section

Line 30: Remove mention of "yarn mappings":
```
- Each version has specific dependency versions and preprocessor definitions
```

Line 31: Update preprocessor example:
```
- The build system generates `build.properties` with preprocessor flags like `MC_VER=26010` for conditional compilation
```

### Step 3: Update "Preprocessor Usage" section

Replace lines 35-42:
```java
#if MC_VER >= 1215
    // Code for 1.21.5+
    return player.getWorld();
#else
    // Code for 1.21.4 and earlier
    return player.getServerWorld();
#endif
```
with:
```java
#if MC_VER >= 26010
    // Code for 26.1+
    return player.level();
#else
    // Code for earlier versions
    return player.getServerWorld();
#endif
```

### Step 4: Update "CI/CD and Testing" section

Lines 124-127: Replace:
```
- Uses Java 21 with Microsoft distribution
```
with:
```
- Uses Java version specified in version_properties (currently Java 25) with Microsoft distribution
```

### Step 5: Create CLAUDE.md as symlink to AGENTS.md

Some tools look for `CLAUDE.md` instead of `AGENTS.md`. Create a symbolic link:

```bash
# On Linux/Mac:
ln -sf AGENTS.md CLAUDE.md

# On Windows (Git Bash or PowerShell as Admin):
# Git Bash:  cmd //c "mklink CLAUDE.md AGENTS.md"
# PowerShell: New-Item -ItemType SymbolicLink -Path CLAUDE.md -Target AGENTS.md
```

If symlinks aren't supported (Windows without developer mode), create a plain file with a reference:
```
See AGENTS.md — this file exists for tool compatibility.
```

Also update `.gitignore` line 43: `/CLAUDE.md` → remove it (since we now track the symlink):
- Remove the line `/CLAUDE.md` from `.gitignore`

**Verify**: `ls -la CLAUDE.md` → shows symlink pointing to `AGENTS.md`

### Step 6: Verify no remaining 1.21 references

**Verify**: `grep -c "1\.21" AGENTS.md` → returns 0

**Verify**: `grep -c "yarn" AGENTS.md` → returns 0 (or only in historical context)

## Test plan

No tests needed — docs-only change. Manual verification:
- Read the updated file and confirm all versions are 26.x
- Confirm build commands use existing versions: `./gradlew listVersions` shows the versions referenced in AGENTS.md

## Done criteria

- [ ] `grep "1\.21" AGENTS.md` returns no matches
- [ ] `grep "yarn" AGENTS.md` returns no matches (unless in a historical changelog context)
- [ ] `grep "Java 21" AGENTS.md` returns no matches
- [ ] Build commands in AGENTS.md reference versions that exist in `version_properties/`
- [ ] `CLAUDE.md` exists as symlink (or reference file) pointing to `AGENTS.md`
- [ ] `.gitignore` no longer ignores `CLAUDE.md`

## STOP conditions

- If `version_properties/` has changed (versions added/removed) since `de3bed4`, stop and re-verify which versions to document

## Maintenance notes

- When new Minecraft versions are added to `version_properties/`, update the "currently supported" list in AGENTS.md
- If the `1.21.X` legacy branch is ever removed from the repo, update AGENTS.md to remove all references to the old version scheme
