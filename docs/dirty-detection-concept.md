# Concept: Fingerprint-based Dirty Detection and Expiry Management

## Overview

The MCP server loads Java projects into an in-memory Spoon `CtModel` at
`load_java_project` time. Once loaded, the model is static — file changes by
external editors go undetected. Projects expire after a configurable timeout
(default 10 minutes). This concept adds:

1. A **fingerprint mechanism** (last-modified timestamp + file size) that
   detects dirty files, warns the agent when query results may be stale, and
   offers an explicit reload path.
2. An **expiry management** that keeps expired projects in memory (marked as
   expired, not deleted), communicates expired project names to the agent, and
   allows bulk reload of all expired projects.

---

## 0. Expiry Management (New)

### 0.1 Problem with current `removeExpired()`

The current implementation in `ProjectManager.removeExpired()` hard-deletes
expired entries from the `ConcurrentHashMap` and cleans up their temp
directories. The agent receives no notification and cannot reload because the
original source string is lost.

### 0.2 New behaviour: mark instead of delete

`removeExpired()` is renamed to `markExpired()`. Instead of removing entries,
it marks them as expired:

```
ProjectEntry gets a new field:
    boolean expired   // false on load, set to true when expiryDate passes

markExpired():
    for each entry where now.isAfter(entry.expiryDate()) and !entry.expired():
        entry = entry.withExpired(true)       // record copy with expired=true
        entries.put(entry.name(), entry)       // replace in map
        log: "Project '{}' expired at {}"
        collect entry.name() into expiredNames list
    return expiredNames
```

The project stays in memory. The model, launcher, fingerprints — all
preserved. The agent can still query it, but tools should warn that the data
is expired.

### 0.3 Communication to the agent

Every tool call that triggers `markExpired()` checks the returned list. If
non-empty, the tool **prepends** an expiry notice to its response:

```
> ⚠️ **Expired projects:** `my-project`, `other-project`
> These projects have passed their expiry date. Data may be stale.
> Call `reload_java_project` with `expired=true` to reload all expired projects.
```

This applies to **all tools**, not just the four detail-critical ones —
because `markExpired()` runs inside `manager.find()` / `manager.list()`,
which every tool calls.

### 0.4 Tool behaviour with expired entries

When a tool queries an expired project:

| Tool action | Behaviour |
|-------------|-----------|
| `find(name)` returns entry with `expired=true` | Tool proceeds normally but prepends expiry warning |
| `list()` returns entries including expired ones | `list_loaded_projects` marks expired projects with `[EXPIRED]` |
| Agent calls `reload_java_project(name=X)` | Reloads X even if not expired (explicit reload) |
| Agent calls `reload_java_project(expired=true)` | Reloads ALL expired projects in one call |

### 0.5 `list_loaded_projects` output with expired entries

```
# Loaded Projects

- **my-project** — 52 types, build: MAVEN [expires: 2026-09-17T12:00:00Z]
- **other-project** (alias: `op`) — 18 types, build: GRADLE **[EXPIRED]**
- **third-project** — 7 types, build: RAW **[EXPIRED]**
```

### 0.6 Cleanup

Actual resource cleanup (deleting temp directories, delombok output) happens
only in two cases:

1. **`reload_java_project`** — before re-parsing, the old resources are cleaned
2. **`unload_java_project`** — explicit removal by the agent

Expired projects that are never reloaded or unloaded remain in memory until
the server shuts down. This is a conscious trade-off: the agent never loses
the ability to reload, at the cost of some memory for stale models.

---

## 1. Data Model Extensions

### 1.1 Fingerprint record

```
record Fingerprint(long lastModified, long fileSize) {}
```

Pure OS-level metadata — no file content read. Cheap to obtain via
`Files.getLastModifiedTime()` and `Files.size()`.

### 1.2 ProjectEntry extensions

The current `ProjectEntry` record:

```
ProjectEntry(
    String name,
    String alias,
    Instant expiryDate,
    Path projectDir,          // ← points to delombok temp dir when delomboked
    Launcher launcher,
    CtModel model,
    String buildType,
    boolean delomboked,
    String lombokVersion
)
```

Extended by five fields:

```
ProjectEntry(
    ...existing fields...,
    Path originalProjectDir,                   // source dir before delombok
    String originalSource,                     // Git URL / local path / archive, for reload
    Map<Path, Fingerprint> sourceFingerprints,  // snapshot taken at load time
    boolean expired                             // false on load, true after expiryDate passes
)
```

**Why `originalProjectDir`:** When delombok runs, `projectDir` is redirected
to the temp output directory. The user edits the original files. Without
storing the original path, fingerprinting would check the wrong (temp) files
and never detect changes.

**Why `originalSource`:** `ProjectLoader.resolveSource()` receives the source
string once and discards it. A reload needs the same source to re-resolve
(re-clone, re-download, or re-read local path). Without persisting it, reload
is impossible.

**Why `sourceFingerprints`:** The map is built once at load time by walking
the original source root and stat-ing every `.java` file. It is the baseline
for all future comparisons.

**Why `expired`:** Marks the project as expired without deleting it. The
agent can still query the (stale) model and can reload using the stored
`originalSource`. Without this flag, `markExpired()` would have to delete
the entry, losing the reload capability.

---

## 2. Fingerprint Collection at Load Time

After `launcher.buildModel()` succeeds in `ProjectManager.load()`, before
storing the entry:

```
1. originalProjectDir = projectDir  (captured before delombok reassigns it)
2. originalSource     = source       (the string passed to load())
3. Walk originalSourceRoot (src/main/java, src, or projectDir)
4. For each .java file: record Fingerprint(lastModified, fileSize)
5. Store in sourceFingerprints map
```

The walk covers all `.java` files under the source root — not just the ones
Spoon successfully parsed. This ensures that files Spoon skipped (parse
errors, unsupported syntax) still have a fingerprint baseline.

---

## 3. Dirty Check: `check_project_dirty` (Full-Scan Status Tool)

### 3.1 Purpose

This is the **central status tool**. It performs a full scan of the entire
source root, comparing every `.java` file on disk against the stored
fingerprint map. It detects:

- **Changed** files (timestamp or size differs)
- **Deleted** files (in fingerprint map, but not on disk)
- **New** files (on disk, but not in fingerprint map)

### 3.2 Parameters

```
name     (string, required)  — project name or alias
fullScan  (boolean, optional) — deprecated; always true.
                                Kept for API stability. The tool always
                                does a full scan — that is its purpose.
```

### 3.3 Response (dirty)

```
# Dirty Check: `my-project`

> ⚠️ **Project is dirty** — 48 source files on disk, 47 in fingerprint map:
> 3 changed, 1 deleted, 1 new since load.

| Status  | File |
|---------|------|
| Changed | src/main/java/com/example/User.java |
| Changed | src/main/java/com/example/Service.java |
| Changed | src/main/java/com/example/Config.java |
| Deleted | src/main/java/com/example/OldUtil.java |
| New     | src/main/java/com/example/NewService.java |

Call `reload_java_project` with name `my-project` to refresh the model.
```

### 3.4 Response (clean)

```
# Dirty Check: `my-project`

> ✅ **Project is clean** — 47 source files checked, no changes since load.
```

### 3.5 New-file detection note

The tool description (shown to the agent) must include:

```
Note: New files created after the initial load are not visible in the
in-memory model. They are only detectable via this full-scan tool, not by
the scoped dirty checks in inspect/list tools. After reload_java_project,
all new, changed, and deleted files are reflected in the model.
```

---

## 4. Scoped Dirty Checks in `inspect_*` and `list_methods`

### 4.1 Principle

The four detail-critical tools (`inspect_class`, `inspect_method`,
`inspect_field`, `list_methods`) perform a **scoped** dirty check — only
the source file(s) of the type(s) they query.

### 4.2 No clean confirmation

These tools **never** output a `✅ Project is clean` message. This would
waste tokens on every call. Only **dirty** triggers a warning.

### 4.3 Warning format (scoped)

When the queried type's source file has changed:

```
> ⚠️ Source file has changed since load: src/main/java/com/example/User.java
> Call reload_java_project to refresh the model.
```

When multiple files are dirty (project-wide `list_methods`):

```
> ⚠️ 3 source files changed since load:
> src/main/java/com/example/User.java, src/main/java/com/example/Service.java, src/main/java/com/example/Config.java
> Call reload_java_project to refresh the model.
```

(Changed file list capped at 10, then "…and N more".)

### 4.4 Which files does each tool check?

| Tool | Files checked | How determined |
|------|---------------|----------------|
| `inspect_class` | 1: `type.getPosition().getFile()` | The type's source file |
| `inspect_method` | 1: same as above | The declaring type's source file |
| `inspect_field` | 1: same as above | The declaring type's source file |
| `list_methods(className=X)` | 1: type X's source file | The filtered type |
| `list_methods` (project-wide) | N: all types' source files, deduplicated | Every type in the model |

### 4.5 Edge cases

| Case | Behavior |
|------|----------|
| `getPosition().getFile()` is `null` (synthetic, delomboked types) | Skip — no fingerprint to compare |
| File was deleted after load | Dirty — stored fingerprint exists, but `Files.exists()` returns false |
| File was renamed/moved | Dirty — old path's fingerprint mismatches |
| New file created externally | **Not detectable** by scoped checks — only `check_project_dirty` finds it |
| `get_file_content` reads live from disk | Response is always current — no check needed |
| Delomboked project | `originalProjectDir` points to original; fingerprint checks original files |

---

## 5. New Tool: `reload_java_project`

### 5.1 Parameters

```
name     (string, required)  — project name or alias of the project to reload
expired   (boolean, optional) — if true, reloads ALL expired projects in
                                addition to the one named by `name`.
                                If `name` is empty/null and `expired=true`,
                                reloads only the expired projects (no
                                additional single project).
                                Default: false
```

### 5.2 Behavior

#### Case A: `expired=false` (default) — single project reload

```
1. Look up existing ProjectEntry by name/alias
2. If not found → error: "No project found with name 'X'. Call load_java_project first."
3. Read stored originalSource and stored load parameters (autoDelombok, etc.)
4. Clean up old resources:
   a. If delomboked: delete temp delombok directory
   b. If project was cloned/downloaded: delete temp project directory
5. Re-resolve originalSource via ProjectLoader.resolveSource()
6. Re-detect build type, re-run delombok if needed
7. Re-create Launcher, re-build CtModel
8. Re-build fingerprint map from fresh source root
9. Set expired=false, update expiryDate to now + default timeout
10. Replace entry in ConcurrentHashMap (same name, same alias)
11. Return success response with new model stats
```

#### Case B: `expired=true` — reload all expired projects (+ optional named one)

```
1. Find all entries where entry.expired() == true
2. If none found → return info: "No expired projects to reload."
3. For each expired entry:
   a. Read stored originalSource and load parameters
   b. Clean up old resources (delombok temp, clone temp)
   c. Re-resolve, re-detect, re-parse, re-fingerprint
   d. Set expired=false, update expiryDate
   e. Replace entry in map
4. If `name` is also provided and that project is NOT already in the
   expired set (i.e. it was not expired but the agent wants to reload it
   too), reload it as in Case A.
5. Return success response listing all reloaded projects
```

### 5.3 Response (success, single project)

```
{
  "error": false,
  "message": "Project reloaded successfully",
  "reloaded": ["my-project"],
  "name": "my-project",
  "alias": "my-alias",
  "types": 52,
  "build": "MAVEN",
  "delomboked": true,
  "filesChecked": 48,
  "filesChanged": 0
}
```

### 5.4 Response (success, expired=true, multiple projects)

```
{
  "error": false,
  "message": "2 expired projects reloaded successfully",
  "reloaded": ["other-project", "third-project"],
  "projects": [
    { "name": "other-project", "alias": "op", "types": 18, "build": "GRADLE", "delomboked": false },
    { "name": "third-project", "alias": "third-project", "types": 7, "build": "RAW", "delomboked": false }
  ]
}
```

### 5.5 Response (expired=true, no expired projects)

```
{
  "error": false,
  "message": "No expired projects to reload.",
  "reloaded": []
}
```

### 5.6 Tool description (shown to the agent)

```
Reloads previously loaded Java project(s) from their original source,
replacing the in-memory model with a fresh parse.

Use this when:
- check_project_dirty or a scoped dirty warning from inspect_class,
  inspect_method, inspect_field, or list_methods indicates stale source files.
- A tool response warns about expired projects.

Parameters:
- name: The project name or alias to reload. Required unless expired=true
  and no specific project is needed.
- expired: If true, reloads ALL projects currently marked as expired. If
  name is also provided, that project is reloaded in addition (unless it
  was already in the expired set). Default: false.

Note: New files created after the initial load are not visible in the model
and cannot be detected by scoped dirty checks. Only check_project_dirty
performs a full scan that accounts for new files. After reload, all new,
changed, and deleted files are reflected in the model.

Projects are reloaded with the same parameters (source, alias, delombok
setting) as the original load. No need to re-specify them. The expiry timer
is reset on reload.
```

---

## 6. Shared Helper in `BaseJavaTool`

### 6.1 `DirtyCheckResult` record

```
record DirtyCheckResult(int filesChecked, List<String> changedFiles) {
    boolean isDirty() { return !changedFiles.isEmpty(); }
}
```

### 6.2 `checkDirty` helper (scoped)

```
protected DirtyCheckResult checkDirty(ProjectEntry entry, List<CtType<?>> typesToCheck) {
    List<String> changedFiles = new ArrayList<>();
    List<Path> sourceFiles = typesToCheck.stream()
        .map(t -> t.getPosition().getFile())
        .filter(Objects::nonNull)
        .distinct()
        .toList();

    for (Path file : sourceFiles) {
        Fingerprint stored = entry.sourceFingerprints().get(file);
        if (stored == null) continue;  // no baseline — skip
        if (!Files.exists(file)) { changedFiles.add(file + " (deleted)"); continue; }
        Fingerprint current = new Fingerprint(
            Files.getLastModifiedTime(file).toMillis(),
            Files.size(file));
        if (!current.equals(stored)) changedFiles.add(file.toString());
    }
    return new DirtyCheckResult(sourceFiles.size(), changedFiles);
}
```

### 6.3 `checkDirtyFullScan` helper (for `check_project_dirty`)

```
protected DirtyCheckResult checkDirtyFullScan(ProjectEntry entry) {
    // Walk originalSourceRoot, compare ALL .java files against fingerprint map.
    // Detects changed, deleted, and new files.
}
```

### 6.4 `formatDirtyWarning` helper (scoped, for inspect/list tools)

```
protected String formatDirtyWarning(DirtyCheckResult dirty) {
    // Returns the ⚠️ warning block without clean confirmation.
    // Changed file list capped at 10, then "…and N more".
}
```

---

## 7. Token Efficiency Rules

| Tool | Dirty warning on stale | Clean confirmation on fresh |
|------|-----------------------|-----------------------------|
| `inspect_class` | Yes (scoped, 1 file) | **No** — token waste |
| `inspect_method` | Yes (scoped, 1 file) | **No** |
| `inspect_field` | Yes (scoped, 1 file) | **No** |
| `list_methods` | Yes (scoped, N files) | **No** |
| `check_project_dirty` | Yes (full scan) | **Yes** — this is the status tool |
| All other tools | No dirty check | No |
| `get_file_content` | No — reads live from disk | No |

---

## 8. Files to Create or Modify

```
NEW FILES:
  model/Fingerprint.java (or inner record in ProjectEntry)
    → record Fingerprint(long lastModified, long fileSize) {}

  tools/CheckProjectDirtyTool.java
    → new tool: full-scan dirty check (changed, deleted, new files)

  tools/ReloadJavaProjectTool.java
    → new tool: reload single project or all expired projects
    → parameters: name (string), expired (boolean, default false)

MODIFIED FILES:
  model/ProjectEntry.java
    → add originalProjectDir, originalSource, sourceFingerprints, expired fields

  ProjectManager.java
    → rename removeExpired() to markExpired(), return List<String> of expired names
    → mark expired=true instead of deleting entries
    → store originalProjectDir + originalSource before delombok
    → build fingerprint map after buildModel()
    → add reload(name) method (single project)
    → add reloadExpired() method (all expired projects)
    → update find() and list() to call markExpired() and propagate expired names

  tools/BaseJavaTool.java
    → add DirtyCheckResult record
    → add checkDirty() scoped helper
    → add checkDirtyFullScan() helper
    → add formatDirtyWarning() helper
    → add formatExpiredWarning(List<String> expiredNames) helper

  tools/InspectClassTool.java
    → call checkDirty, prepend warning only if dirty
    → call markExpired, prepend expiry warning if any expired

  tools/InspectMethodTool.java
    → call checkDirty, prepend warning only if dirty
    → call markExpired, prepend expiry warning if any expired

  tools/InspectFieldTool.java
    → call checkDirty, prepend warning only if dirty
    → call markExpired, prepend expiry warning if any expired

  tools/ListMethodsTool.java
    → call checkDirty (scoped), prepend warning only if dirty
    → call markExpired, prepend expiry warning if any expired

  tools/ListLoadedProjectsTool.java
    → mark expired projects with [EXPIRED] in output

  JavaMcpServer.java
    → register CheckProjectDirtyTool + ReloadJavaProjectTool in toolSpecs
```

---

## 9. Author Attribution

All new and modified Java files carry the MIT License 2026 header with:

```
@author Janusch Rentenatus
```

This replaces the previous author attribution. Existing files retain their
header but the `@author` line is updated to `Janusch Rentenatus` for all files
touched in this change.

---

## 10. Limitations and Open Questions

| # | Limitation | Impact | Mitigation |
|---|------------|--------|------------|
| 1 | New files not detectable by scoped checks in `inspect_*` / `list_methods` | Agent misses newly created types | `check_project_dirty` full scan detects them; `reload_java_project` incorporates them |
| 2 | Build files (`pom.xml`, `build.gradle`) not fingerprinted | Dependency/classpath changes undetected | Future extension: fingerprint build files separately |
| 3 | Delombok on reload can take minutes | `reload_java_project` blocks | No workaround — Spoon has no incremental parse. Agent should inform user. |
| 4 | Fingerprint is lastModified + size, not content hash | Identical-timestamp + identical-size edits go undetected | Extremely unlikely in practice; SHA-256 would be more robust but requires reading every file |
| 5 | `getPosition().getFile()` may be null for delomboked types | No fingerprint check for those | Acceptable — delombok temp files don't change; original files are fingerprinted via `originalProjectDir` |
| 6 | Concurrent file edits during a tool call | Race condition: file changes between check and response | Acceptable — the warning is advisory, not a guarantee. Agent re-checks on next call. |
| 7 | `originalSource` was a Git URL and remote has changed | Reload re-clones HEAD — may differ from original commit | Document in tool description. For pinned commits, the source string already contains the hash. |
| 8 | Clean confirmation only in `check_project_dirty` | Agent cannot get clean status from other tools without calling the status tool | By design — prevents token waste on every `inspect_*` call. Agent calls `check_project_dirty` when it needs a full status. |
| 9 | Expired projects stay in memory until reloaded, unloaded, or server shutdown | Memory cost for stale models | Conscious trade-off: agent never loses reload capability. If memory is critical, `unload_java_project` frees resources. |
| 10 | `reload_java_project(expired=true)` reloads all expired sequentially | Multiple expired projects with delombok → long blocking call | Agent should inform user. No parallel reload — Spoon Launcher creation is not thread-safe. |
| 11 | `markExpired()` runs on every `find()`/`list()` call | Slight overhead per tool call | `markExpired()` is a cheap timestamp comparison over the entries map — O(n) where n is number of loaded projects (typically 1-5). |

---

## 11. Implementation Order

| Step | Commit | Files |
|------|--------|-------|
| 1 | Fingerprint record + ProjectEntry extensions (incl. `expired` field) | `Fingerprint.java`, `ProjectEntry.java` |
| 2 | `markExpired()` replacing `removeExpired()` — mark instead of delete, return expired names | `ProjectManager.java` |
| 3 | Propagate expired names to all tool responses via `formatExpiredWarning` | `BaseJavaTool.java`, all tools calling `find()`/`list()` |
| 4 | `list_loaded_projects` marks expired projects with `[EXPIRED]` | `ListLoadedProjectsTool.java` |
| 5 | Fingerprint collection at load time + store originalProjectDir/source | `ProjectManager.java` |
| 6 | `checkDirty` + `checkDirtyFullScan` + `formatDirtyWarning` helpers | `BaseJavaTool.java` |
| 7 | `check_project_dirty` tool (full scan, clean + dirty) | `CheckProjectDirtyTool.java`, `JavaMcpServer.java` |
| 8 | `reload_java_project` tool (single + `expired=true` bulk reload) | `ReloadJavaProjectTool.java`, `ProjectManager.java`, `JavaMcpServer.java` |
| 9 | Scoped dirty warnings in `inspect_class` | `InspectClassTool.java` |
| 10 | Scoped dirty warnings in `inspect_method` | `InspectMethodTool.java` |
| 11 | Scoped dirty warnings in `inspect_field` | `InspectFieldTool.java` |
| 12 | Scoped dirty warnings in `list_methods` | `ListMethodsTool.java` |
