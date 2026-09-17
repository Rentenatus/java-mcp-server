# java-mcp-server

**Version 1.0.6** — MIT License

A deterministic Java code analysis server that implements the Model Context Protocol (MCP) to give AI agents precise, structured access to Java source code.

## Why

When AI agents attempt to analyse or refactor Java codebases using purely textual prompts (prompting with the whole file, retrieval-augmented generation, or letting the agent read source files on its own), they face fundamental problems:

- **Hallucination**: Without an exact model of the code, the AI guesses type hierarchies, method signatures, and references — often confidently wrong.
- **Inconsistency**: Raw text lacks the resolved symbol table, type information, and cross-references that a compiler or static analyser provides.
- **Inefficiency**: Sending entire source files as context is wasteful; structured queries (`find all implementations of this interface`, `show me the method body of X`) are far more efficient.

**java-mcp-server** solves this by exposing a rich set of deterministic tools over MCP. It uses Spoon to parse Java source into a full AST, resolves types, references, annotations, and structure — then makes everything available to an AI agent as individual, verifiable tools. The agent never guesses; it queries.

> ⚠️ This project is under active development. Features and APIs may change.

## Features

- **Load projects** from local paths, Git URLs, or archives (.zip, .tar.gz)
- **Auto-detect** Maven and Gradle builds; uses Spoon's MavenLauncher when possible for full classpath resolution
- **Browse** packages, classes, methods, fields, constructors, enums, annotations
- **Inspect** method bodies, annotations, type hierarchies, field declarations
- **Search** source code across the project or across multiple loaded projects
- **Cross-reference**: find implementations, usages, annotated elements, method invocations, callers
- **Validate** code references (check if a type/method/field actually exists)
- **Multi-project**: load and query several projects simultaneously
- **Fingerprint-based dirty detection**: detects changed, deleted, and new source files since load
- **Expiry management**: expired projects stay in memory (marked, not deleted) and can be bulk-reloaded
- **Javadoc support**: `inspect_class`, `inspect_method`, `inspect_field`, and `list_methods` include Javadoc via `withJavadoc=true` (default)
- **Server version**: displayed at startup and in the `check_project_dirty` status output

### Available tools (29)

| Tool | Description |
|---|---|
| `load_java_project` | Load a Java project from a path, Git URL, or archive. Supports optional `delombok` (default true) to expose Lombok-generated members in the AST. |
| `unload_java_project` | Unload a project and free resources |
| `reload_java_project` | Reload a project from its original source. Supports `expired=true` to bulk-reload all expired projects. |
| `list_loaded_projects` | List all currently loaded projects (expired projects marked with `[EXPIRED]`) |
| `check_project_dirty` | Full-scan status tool: compares all `.java` files on disk against stored fingerprints. Detects changed, deleted, and new files. Shows MCP server version. |
| `project_metadata` | Get metadata (name, build type, type count) |
| `inspect_build_config` | Show detected build configuration |
| `list_packages` | List all packages in the project |
| `list_classes` | List all classes, optionally filtered by package |
| `list_methods` | List all methods in a class or project-wide. With `withJavadoc=true` (default), includes a one-line Javadoc summary per method. |
| `inspect_class` | Deep-dive into a class: fields, methods, superclass, interfaces, annotations. Includes class-level Javadoc with `withJavadoc=true` (default). For Lombok projects loaded with `delombok=false`, appends a `Lombok-predicted members` section. |
| `inspect_method` | Deep-dive into a method: signature, parameters, return type, body. Includes full method Javadoc with `withJavadoc=true` (default). |
| `inspect_field` | Deep-dive into a field: type, modifiers, annotations, initializer. Includes field Javadoc with `withJavadoc=true` (default). |
| `list_constructors` | List all constructors in a class with parameters and bodies |
| `list_annotations` | List all annotations used in the project or on a specific type |
| `find_annotated_elements` | Find all types/methods/fields annotated with a given annotation |
| `get_type_hierarchy` | Show superclass and interface hierarchy for a type |
| `get_annotation_details` | Show annotation definition with its attributes |
| `list_dependencies` | List external dependencies by scanning imports |
| `list_enum_constants` | List all constants of an enum |
| `resolve_type` | Resolve a simple class name to fully qualified name(s) |
| `validate_code_reference` | Check if a type/method/field exists in the project |
| `find_implementations` | Find concrete implementations of an interface or abstract class |
| `get_file_content` | Read the raw source file for a class |
| `list_methods_by_return_type` | Find methods returning a specific type |
| `list_method_invocations` | List all method calls inside a method's body |
| `find_references` | Find all references to a type, method, or field |
| `multi_file_search` | Search for text across all loaded projects |
| `search_source` | Search for text within the source of a specific project |

## License

MIT — see LICENSE.

## Building

Prerequisites: Java 17+ and Maven.

```bash
mvn clean package -DskipTests
```

This produces a fat JAR at `target/java-mcp-server-standalone.jar` containing all dependencies.

## Running

```bash
java -jar target/java-mcp-server-standalone.jar
```

The server communicates over **stdin/stdout** using the MCP transport protocol, so it is designed to be launched as a subprocess by an MCP client (e.g., an AI agent framework).

Logs are written to `/tmp/java_mcp_server.log` by default (configurable in `src/main/resources/application.yaml`).

## Configuration for AI agents

### Mistral Vibe (TOML)

Add to `~/.vibe/config.toml`:

```toml
[[mcp_servers]]
name = "my-mcp-server"
transport = "stdio"
command = "java"
args = ["-jar", "/path/to/java-mcp-server-standalone.jar"]
```

Or add non-interactively from the shell:

```bash
vibe mcp add my-mcp-server \
  --transport stdio \
  --command java \
  --args '["-jar", "/path/to/java-mcp-server-standalone.jar"]'
```

### Claude Desktop / Claude Code

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "java-mcp": {
      "command": "java",
      "args": ["-jar", "/path/to/java-mcp-server-standalone.jar"],
      "env": {}
    }
  }
}
```

### Continue.dev

Add to your `config.json`:

```json
{
  "experimental": {
    "mcpServers": {
      "java-mcp": {
        "command": "java",
        "args": ["-jar", "/path/to/java-mcp-server-standalone.jar"]
      }
    }
  }
}
```

### Cline / Roo Code / OpenCode

Edit the MCP settings file for your editor/agent. The typical configuration is:

```json
{
  "mcpServers": {
    "java-mcp": {
      "command": "java",
      "args": ["-jar", "/path/to/java-mcp-server-standalone.jar"]
    }
  }
}
```

### Generic MCP client

Any MCP-compatible client can invoke the server as a subprocess:

```bash
java -jar /path/to/java-mcp-server-standalone.jar
```

The server will listen on stdin for JSON-RPC messages and respond on stdout.

## How it works

1. The server loads a Java project using Spoon, optionally leveraging Maven/Gradle metadata for full classpath resolution.
2. If Lombok is detected and `delombok=true` (default), the project is first run through `delombok` so that Lombok-generated members (`getX()`, `setX()`, `equals()`, etc.) become real AST nodes. If `delombok=false`, those members are absent from the model but `inspect_class` lists them under a `Lombok-predicted members` section as a safety net.
3. Spoon builds a full AST (CtModel) with resolved types and references. Comment parsing is enabled, so Javadoc is available via `getDocComment()`.
4. Each MCP tool maps to a precise query against that model — no guessing, no hallucination.
5. Results are returned as structured text that the agent can safely reason about.

### Expiry and dirty detection

- Projects auto-expire after 10 minutes by default (configurable via `expiryDate` when loading).
- Expired projects are **marked**, not deleted — they stay in memory so the agent can reload them using `reload_java_project` with `expired=true`.
- Every tool response includes an expiry warning when projects have expired, listing the affected project names.
- `check_project_dirty` performs a full filesystem scan, comparing file timestamps and sizes against stored fingerprints. It detects **changed**, **deleted**, and **new** files.
- `inspect_class`, `inspect_method`, `inspect_field`, and `list_methods` perform scoped dirty checks on the specific source files they query. They warn only when dirty (no clean confirmation — token efficiency).

### Javadoc support

Four tools accept a `withJavadoc` parameter (default `true`):

| Tool | What it shows |
|------|---------------|
| `inspect_class` | Full class-level Javadoc in a code block |
| `inspect_method` | Full method Javadoc (including `@author`) in a code block |
| `inspect_field` | Full field Javadoc in a code block |
| `list_methods` | First non-empty summary line per method (skips `*` and `@` lines) |

## Lombok support

Lombok is an annotation processor that generates code at compile time. Spoon alone sees only the annotations (`@Data`, `@Getter`, …) and not the methods Lombok would generate, which can lead to incomplete analysis. To handle this, `java-mcp-server` ships with a built-in delombok pipeline:

- Auto-detects Lombok in `pom.xml` or `build.gradle[.kts]`.
- Locates the Lombok JAR in `~/.java-mcp-server/lombok/` or downloads it from Maven Central.
- Runs `delombok` on the project sources, producing a fully expanded directory.
- Feeds that directory to Spoon so generated getters/setters/equals/etc. are real AST members.

Disable with `delombok=false` if you want raw source analysis or are debugging.

## Logs

By default, logs are written to `/tmp/java_mcp_server.log` (configurable via `src/main/resources/application.yaml`).
