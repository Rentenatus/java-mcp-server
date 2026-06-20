# java-mcp-server

A deterministic Java code analysis server that implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io) to give AI agents precise, structured access to Java source code.

## Why

When AI agents attempt to analyse or refactor Java codebases using purely textual prompts (prompting with the whole file, retrieval-augmented generation, or letting the agent read source files on its own), they face fundamental problems:

- **Hallucination**: Without an exact model of the code, the AI guesses type hierarchies, method signatures, and references — often confidently wrong.
- **Inconsistency**: Raw text lacks the resolved symbol table, type information, and cross-references that a compiler or static analyser provides.
- **Inefficiency**: Sending entire source files as context is wasteful; structured queries (`find all implementations of this interface`, `show me the method body of X`) are far more efficient.

**java-mcp-server** solves this by exposing a rich set of deterministic tools over MCP. It uses [Spoon](https://spoon.gforge.inria.fr/) to parse Java source into a full AST, resolves types, references, annotations, and structure — then makes everything available to an AI agent as individual, verifiable tools. The agent never guesses; it queries.

This project is directly inspired by [cobol-mcp-server](https://github.com/aferreiraguido/cobol-mcp-server), which applies the same idea to COBOL. The goal is the same: **bring determinism to AI-driven code analysis** by grounding every statement in what the parser actually found.

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

### Available tools

| Tool | Description |
|---|---|
| `load_java_project` | Load a Java project from a path, Git URL, or archive |
| `list_loaded_projects` | List all currently loaded projects |
| `unload_java_project` | Unload a project and free resources |
| `project_metadata` | Get metadata (name, build type, type count) |
| `inspect_build_config` | Show detected build configuration |
| `list_packages` | List all packages in the project |
| `list_classes` | List all classes, optionally filtered by package |
| `list_methods` | List all methods in a class or project-wide |
| `inspect_class` | Deep-dive into a class: fields, methods, superclass, interfaces, annotations |
| `inspect_method` | Deep-dive into a method: signature, parameters, return type, body |
| `inspect_field` | Deep-dive into a field: type, modifiers, annotations, initializer |
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

MIT — see [LICENSE](LICENSE).

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
2. Spoon builds a full AST (CtModel) with resolved types and references.
3. Each MCP tool maps to a precise query against that model — no guessing, no hallucination.
4. Results are returned as structured JSON that the agent can safely reason about.

Projects auto-expire after 10 minutes by default. Use the `expiryDate` parameter when loading to override.
