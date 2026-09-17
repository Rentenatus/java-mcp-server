# Edit Tools — Concept

Extending `java-mcp-server` from read-only analysis to deterministic code modification.

Status: Concept — not implemented.

---

## 1. Motivation

The server can currently load, inspect, search, and cross-reference Java source code. It cannot modify any of it. An AI agent that analyses a codebase must therefore emit a textual diff and rely on a human to copy it into the IDE. That hand-off is slow, error-prone, and wastes the one asset the server already has: a fully resolved AST with exact reference tables.

Edit tools close that gap. The LLM proposes *what* to change; the server executes *how*, deterministically, against the same AST model that powers the read tools. The human becomes reviewer, not operator.

### Design principles

1. **The model decides, the server deterministically executes.** The LLM is the architect; the server is the craftsman. Neither role bleeds into the other.

2. **Make ambiguity visible, not resolved.** The server cannot resolve every ambiguity — reflection, type erasure, unloaded modules. It does not pretend to. Instead, every unresolved ambiguity is surfaced as a structured warning in the tool response. A hidden risk becomes a visible risk. This principle recurs across the entire edit toolset: the server does not guess, does not silently skip, does not return partial success. What it cannot guarantee, it names explicitly.

---

## 2. Mutability Classification

Not every loaded project may be edited. Three categories exist.

| Source | Mutable | Default `editable` |
|--------|---------|--------------------|
| Local path | Yes | `true` |
| Git URL (cloned) | Yes | `true` |
| Archive (.zip, .tar.gz, unpacked) | Conditional | `true` |
| JAR file | No | `false` (not overridable) |

Rules:

- `load_java_project` gains an `editable` parameter. Default follows the table above; explicitly overridable except for JAR, which is hard-locked to `false`.
- `list_loaded_projects` reports `editable: true|false` per project.
- On a non-mutable project, every edit tool returns a structured error — no exception, no partial success:

  `Project 'foo-lib' is read-only (loaded from JAR). Edit tools are not available.`

- **Paranoia mode:** a local-path project may be loaded with `editable: false` even though the filesystem is writable. All edit tools then behave as if the project were a JAR. This is the emergency exit for users who do not trust the agent with write access.

### Multi-module awareness

Maven and Gradle projects often span multiple modules. A rename or signature change must reach callers in all modules — but only loaded modules are in the server's AST.

- `load_java_project` and `project_metadata` report `modules_detected` and `modules_loaded`.
- When `modules_detected != modules_loaded`, every edit tool appends a warning to its response:

  `WARNING: 3 modules detected, 1 loaded. Edits may miss references in 2 unloaded modules: 'app-test', 'app-web'. Load all modules before editing for full coverage.`

- The server does not force all modules to be loaded — that is a memory and load-time trade-off the caller controls. It makes the gap visible.

---

## 3. Edit Tools

Thirteen tools, grouped by scope.

### Line-ending normalization

The server is designed for many projects, not one. Line-ending conventions vary: LF on Linux/macOS, CRLF on Windows, occasionally mixed within a single file. Every text-matching operation — `edit_line`, signature matching in `replace_method_body`, annotation duplicate checks — must be robust against all conventions.

**Rule: CR is stripped from both source and target before any string comparison.** The server normalizes the file content (source) and the search/replace strings (target) by removing all carriage returns, so that LF is always recognized as the line separator. Matching succeeds regardless of whether the file uses CRLF or LF.

**Normalization is for matching only, not for output.** When writing to disk, the server preserves the file's existing line-ending convention. A CRLF file stays CRLF; an LF file stays LF. This keeps diffs clean — only the intended semantic changes appear, never a wholesale line-ending conversion that would noise every line in the file.

### Symbol-level operations

#### `rename_symbol`

Rename a class, method, field, or local variable across every caller.

- Inputs: `oldName`, `newName`, optional `scope` (class/method/field/variable).
- Backed by `find_references`, which is already type-resolved. The server filters the reference list to the exact signature (critical for overloaded methods) and replaces the identifier at each call site.
- The LLM supplies the new name; the server guarantees every AST reference is updated.

**Generics preservation.** Rename and signature operations must preserve type parameters and bounds. `public <T extends Comparable<T>> T foo(T input)` renamed to `bar` produces `public <T extends Comparable<T>> T bar(T input)`. Type parameters, bounds, and wildcard captures are structural components of the AST node, not text fragments — they are carried through the rewrite intact.

**Unresolved references (reflection boundary).** Static reference resolution covers AST call sites. It does not cover string-based references — `Class.getMethod("oldName")`, configuration files, annotation processors, serialization mappings. After every rename, the server scans all loaded source files for string literals that match the old name and returns them in an `unresolved_references` field:

```json
{
  "renamed": "foo -> bar",
  "callersUpdated": 14,
  "unresolvedReferences": [
    { "file": "FooService.java", "line": 230, "match": "\"foo\"", "context": "Class.getMethod(\"foo\")" },
    { "file": "BarConfig.java", "line": 115, "match": "\"foo\"", "context": "@RequestMapping(\"foo\")" }
  ]
}
```

The server cannot prove these strings refer to the renamed symbol. It also cannot prove they do not. It surfaces them for manual review. This is the visible-ambiguity principle in action.

#### `rewrite_signature`

Add, remove, or rename parameters; change the return type.

Two modes:

| Mode | Behavior |
|------|----------|
| `signature_only` | Changes the declaration only. Callers are left inconsistent; the agent must repair them separately. |
| `signature_and_callers` | Changes the declaration and updates every caller. For added parameters with a default value, the server inserts the default at each call site. For removed parameters, the server drops them only if the call site passes the default value; otherwise it aborts with a per-site error. |

Error example:

`Cannot safely remove parameter at Foo.java:42 — caller passes expression 'getX()' which may have side effects. Manual review required.`

On abort, no change is written. The agent receives the full list of blocking call sites.

Like `rename_symbol`, `rewrite_signature` returns `unresolved_references` for string literals matching the old method name.

**Type erasure boundary.** When `add_method` (below) introduces a method whose erased signature collides with an existing one, the server cannot detect this without compiling. However, `add_method` and `rewrite_signature` perform a **type erasure check** against the loaded AST: if two methods would have the same erased parameter types, the server warns:

`WARNING: Type erasure clash — method 'foo(List<String>)' has the same erased signature as existing method 'foo(List<Integer>)' at Foo.java:18. Java does not permit both. This will not compile.`

This is not a full compiler check — it cannot catch erasure clashes involving types from unloaded modules or external JARs. It catches the common case and warns. What it cannot verify, it names.

#### `remove_member`

Remove a method, field, class, or constructor.

| Mode | Behavior |
|------|----------|
| `safe` (default) | Runs `find_references`. If references exist, returns an error listing them. If zero references, removes. |
| `hard` | Removes regardless of references. Returns a warning listing dangling references that will no longer compile. |

`hard` is the equivalent of `git rm --force` — available, but a deliberate act.

`remove_member` returns `unresolved_references` for string literals matching the removed member name, just like `rename_symbol`.

#### `move_class`

Move a class to a different package.

- Physically moves the source file.
- Rewrites the `package` declaration.
- Updates every `import` statement in every file that references the class, across all loaded projects.
- Appends multi-module warning if applicable.

`move_class` is `rename_symbol` (class) plus package relocation plus import rewrite.

### Method-level operations

#### `replace_method_body`

Replace the entire body of an existing method.

- Inputs: `className`, `methodName`, `signature`, `newBody`.
- Signature must match exactly. No fuzzy matching. On mismatch:

  `No method matching signature 'foo(int, String)' found in class Bar. Available: foo(int), foo(String).`
- **Automatic import resolution.** The server parses `newBody`, identifies type references that are not resolvable in the current import scope, searches the project and declared dependencies for matching types, and inserts the necessary import statements automatically. If a type cannot be resolved:

  `Cannot resolve type 'LocalDateTime' in method body. No matching import found in project or declared dependencies. Provide the fully qualified name or add the dependency.`

  No guessing, no silent skip. Unresolved types block the edit.

#### `add_method`

Add a new method to an existing class.

- Inputs: `className`, `methodName`, `returnType`, `parameters`, `modifiers`, optional `body`.
- Checks for existing methods with the same name and same erased signature. On collision:

  `Method 'foo(List<String>)' already exists in class Bar with the same erased signature.`
- **Automatic import resolution** applies (same behavior as `replace_method_body`).

#### `add_field`

Add a new field to an existing class.

- Inputs: `className`, `fieldName`, `type`, `modifiers`, optional `initializer`.
- **Automatic import resolution** applies to the field type and initializer expression.

### Annotation operations

#### `add_annotation`

Add an annotation to a class, method, or field.

- Inputs: `targetType` (`class` | `method` | `field`), `targetName`, `annotation` (fully qualified or simple name), optional `attributes` (key-value pairs).
- Example: `add_annotation({ targetType: "method", targetName: "handleRequest", annotation: "Override" })`
- Example: `add_annotation({ targetType: "method", targetName: "handleRequest", annotation: "RequestMapping", attributes: { "value": "/new" } })`
- Checks for duplicates. If the annotation is already present with the same attributes, returns an error. If present with different attributes, offers `edit_annotation` instead.
- **Automatic import resolution** applies to the annotation type.

#### `remove_annotation`

Remove an annotation from a class, method, or field.

- Inputs: `targetType`, `targetName`, `annotation`.
- If the annotation is not present, returns an error — no silent no-op.

#### `edit_annotation`

Change the attributes of an existing annotation.

- Inputs: `targetType`, `targetName`, `annotation`, `newAttributes`.
- Example: `edit_annotation({ targetType: "method", targetName: "handleRequest", annotation: "RequestMapping", newAttributes: { "value": "/new" } })`
- If the annotation is not present, returns an error pointing to `add_annotation`.

### File-level operations

#### `edit_line`

Replace a single line in a source file.

- Inputs: `file`, `lineNumber`, `newContent`.
- The primitive escape hatch. When no higher-level tool fits, the agent can drop to line level. This is the tool that makes the edit toolset complete: any edit expressible as a line replacement is possible.
- Does **not** trigger automatic import resolution — it operates below the AST level. The agent is responsible for import correctness when using this tool.
- **Line-ending normalization.** CR characters are stripped from both the file content and `newContent` before the line is matched and replaced, so the edit works regardless of CRLF or LF. The written line adopts the file's existing line ending.

### Structural operations

#### `add_package`

Create a new package.

- Creates the directory.
- Optionally generates `package-info.java`.

#### `add_class`

Create a new class, enum, interface, or abstract class in a package.

- Inputs: `packageName`, `className`, `type` (`class` | `enum` | `interface` | `abstract`), optional `body`.
- **Automatic import resolution** applies to types in `body`.

---

## 4. Transaction Layer

Writing directly to disk after every edit call is unsafe when an agent chains multiple edits and a later step fails. The first edits are already on disk, the project is left inconsistent, and there is no undo.

### Optional transactions

| Tool | Behavior |
|------|----------|
| `begin_transaction` | Starts a transaction. Subsequent edit calls accumulate changes in memory. |
| `commit_transaction` | Writes all accumulated changes to disk in one pass. Fingerprints are re-set. |
| `rollback_transaction` | Discards all accumulated changes. Fingerprints unchanged. |

Without `begin_transaction`, every edit call is autonomous — written immediately. This preserves backward compatibility and avoids overhead for trivial single-edit operations.

After `commit`, all fingerprints are re-set so subsequent read operations see the changes as clean, not dirty.

After `rollback`, the original fingerprints remain valid.

### Commit failure: atomicity guarantee

`commit_transaction` must be **atomic**: either all files are written, or none are. A partial commit leaves the project in an inconsistent state that is worse than no commit at all.

Mechanism:

1. Before writing any file, the server pre-validates all target paths: writable, disk space available (best-effort check), no lock conflicts.
2. Files are written to temporary paths first (`<file>.mcp-tmp`), then renamed to their final destination. Rename is atomic on all major filesystems.
3. If any write fails, all already-written temporary files are deleted. The original files are untouched — they were never overwritten, only the temp files existed.
4. Fingerprints are re-set only after every rename succeeds.
5. On failure, `commit_transaction` returns a structured error:

   `Commit failed at file 5 of 7 (FooService.java): Disk full. All changes rolled back. No files were modified. Transaction is still open — fix the issue and retry, or call rollback_transaction.`

6. The transaction remains open after a failed commit. The agent can fix the problem and retry, or roll back. No silent closure.

---

## 5. Concurrency Control: Optimistic Locking

### The problem

The entire concept originally assumed a single session editing a single project. In reality, multiple agents may work on the same repository — two Vibe sessions on the same machine, a second agent on a cloned copy, or a human editing in an IDE while an agent edits via the server.

If agent A rewrites `Foo.java` and agent B's in-memory AST is stale, agent B's next edit will overwrite agent A's changes. The result is silent data loss — the most expensive failure class.

### The mechanism: fingerprint-based optimistic lock

Every edit tool that writes to a file accepts an optional `expectedFingerprint` parameter. This is the fingerprint the caller believes the file currently has.

Validation logic:

| Condition | Result |
|-----------|--------|
| `expectedFingerprint` matches in-memory fingerprint | Edit proceeds. |
| `expectedFingerprint` not provided | Server checks in-memory fingerprint against disk. If match, proceeds. If mismatch (file changed externally), aborts. |
| `expectedFingerprint` provided but does not match in-memory | Aborts: file was modified since the caller's last observation. |
| In-memory fingerprint does not match disk (external change) | Aborts regardless of `expectedFingerprint`. |

Error on abort:

`File 'Foo.java' has been modified since project load (fingerprint mismatch). Another session or external process changed this file. Reload the project before editing.`

This is the `--force-with-lease` principle from Git: you assert you know the current state, and if the state has moved, the system refuses.

### Why optimistic, not pessimistic

File-level locking (pessimistic) costs overhead on every edit, even when no conflict exists. Optimistic locking costs nothing in the common case (single session) and fails explicitly in the conflict case. Conflicts are rare; when they occur, they must be loud, not silent.

### Cross-session fingerprint sharing

Fingerprints are stored per-server-process. Two sessions on the same machine have independent fingerprint stores. The disk is the shared truth — the optimistic check compares the in-memory fingerprint against the actual file on disk at edit time, catching changes from any source: another server process, an IDE, a manual edit, a build tool.

---

## 6. Fingerprint and Dirty Detection Integration

The existing fingerprint system (timestamp + size per `.java` file) must remain coherent after edits. Three requirements:

1. **After every edit, re-set fingerprints for affected files.** Otherwise the next `inspect_class` on an edited file falsely reports "dirty" for a change the server itself made. Edit → fingerprint updated → next read sees clean.

2. **Transaction-aware fingerprints.** Inside a transaction, fingerprints are not updated until `commit`. If a read tool is called mid-transaction, it must reflect the in-memory state, not the on-disk state. On `rollback`, fingerprints revert to the pre-transaction baseline.

3. **Concurrency-aware fingerprints.** Before any edit, the server compares the in-memory fingerprint against the actual file on disk. If the disk file has changed since load (external modification), the edit aborts. This is the optimistic-lock check described in Section 5.

This is the key design break from the read-only world: fingerprints are no longer only compared, they are actively managed by the edit tools and checked against the filesystem at write time.

---

## 7. Safety Net

Five guarantees that must hold before any edit tool is released:

1. **JAR projects are immune.** Edit tools refuse service with a clear, structured error. No exception, no partial success, no silent skip.

2. **Dirty detection stays intact.** After every edit, fingerprints are re-set. The next read operation sees the server's own changes as clean.

3. **Paranoia mode.** Any project can be loaded with `editable: false`, even a local path on a writable filesystem. All edit tools then behave as if the project were read-only.

4. **Backup-on-first-edit.** Before the first edit is applied to a project, the server copies all source files to `~/.java-mcp-server/backups/<project>-<timestamp>/`. Automatic, no agent request needed. If something goes wrong, a recovery baseline exists.

5. **Optimistic locking on every write.** No edit tool writes to a file without verifying the fingerprint. Silent data loss from concurrent edits is structurally impossible — the system aborts before writing, not after.

---

## 8. Human-in-the-Loop: `get_edit_summary`

After a session of edits, the human needs to review what changed before committing to version control. The server is not a Git client — it does not commit. It provides the diff for the human to review.

`get_edit_summary` returns a compact list of everything changed since the project was loaded:

```
Edit summary for project 'my-app' (since load)
├── Files changed: 7
├── rename_symbol: 2 methods renamed, 14 callers updated
│   └── unresolved_references: 2 string literals match old name (FooService.java:230, BarConfig.java:115)
├── add_class: 1 class added (com.example.NewService)
├── rewrite_signature: 1 method signature changed, 3 callers updated
├── remove_member: 1 field removed (safe mode, 0 references)
└── add_annotation: 1 annotation added (@Transactional on processOrder)
```

The summary includes `unresolved_references` for every operation that produced them. The human sees not only what was changed, but what the server could not verify. The agent was the craftsman; the human is the gatekeeper — and the gatekeeper sees the full picture, including the blind spots.

---

## 9. Rollout Order

Build incrementally. Optimistic locking is a prerequisite — no edit tool ships without it.

| Phase | Tools | Validates |
|-------|-------|-----------|
| 0 | Optimistic locking infrastructure | Fingerprint check at write time, concurrency safety |
| 1 | `rename_symbol`, `edit_line` | Reference resolution + file-write pipeline + fingerprint re-set |
| 2 | `replace_method_body` | File-write pipeline with auto-import resolution |
| 3 | `add_method`, `add_field` | Structural additions with auto-import + erasure check |
| 4 | `remove_member` | Safe-mode reference check + unresolved references |
| 5 | `add_annotation`, `remove_annotation`, `edit_annotation` | Annotation-level edits |
| 6 | `rewrite_signature` | Most complex; depends on rename and reference infrastructure |
| 7 | `add_package`, `add_class`, `move_class` | Structural, can come last |
| 8 | Transaction layer (`begin`/`commit`/`rollback`) | Once tools are stable individually; includes atomic-commit guarantee |

Optimistic locking is Phase 0. It must exist before `rename_symbol` — the first edit tool — enters testing. No exceptions.

---

## 10. Non-Goals

- The server does not compile. It does not run `javac` or `mvn compile`. Compilation is the agent's or human's responsibility. The server performs a best-effort type erasure check on the loaded AST, but this is not a compiler.
- The server is not a Git client. It does not commit, branch, or push.
- The server does not resolve merge conflicts. If a file was changed externally since load, the edit tools fail with a fingerprint-mismatch error and require a reload.
- The server does not format code. It writes syntactically valid Java, but stylistic formatting is the IDE's job.
- The server does not resolve reflection-based references. String literals matching a renamed or removed symbol are surfaced as `unresolved_references` — the server makes them visible, it does not resolve them.
- The server does not detect erasure clashes involving types from unloaded modules or external JARs. The erasure check covers the loaded AST only. Uncovered cases are a known boundary, not a silent gap.

---

## 11. Known Boundaries

Explicitly documented limits of static analysis. The server does not hide these — it surfaces them at every relevant edit operation.

| Boundary | What is not covered | How the server responds |
|----------|---------------------|------------------------|
| Reflection | String-based method/field references (`Class.getMethod("foo")`) | `unresolved_references` field in rename/remove/signature responses |
| Type erasure | Erasure clashes involving unloaded modules or external JARs | Best-effort erasure check on loaded AST; uncovered cases not detected |
| Unloaded modules | Callers in Maven/Gradle modules that were not loaded | Warning when `modules_detected != modules_loaded` on every edit call |
| External changes | File modifications by IDE, other sessions, or build tools | Optimistic lock — fingerprint mismatch aborts the edit |
| Annotation processors | Code generated at compile time by annotation processors | Not visible in the AST; not covered by any edit tool |

These boundaries are not failures — they are the inherent limits of static analysis. The server's contract is: **what it can see, it changes correctly; what it cannot see, it names explicitly.**
