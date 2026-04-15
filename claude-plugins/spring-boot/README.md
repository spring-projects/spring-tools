# Spring Boot Language Server — Claude Code Plugin

A [Claude Code](https://code.claude.com) plugin that contributes the Spring Boot Language Server, providing real-time diagnostics, completions, and navigation for Spring Boot projects.

Unlike the VS Code extension, this plugin uses the **standalone** variant of the language server which operates **without** JDT Language Server. Project classpath is computed directly via Maven and Gradle tooling; type indexing uses Jandex.

## Requirements

- Java 21 or higher on `PATH`
- Maven or Gradle projects in your workspace

## Build

Run the build script once to compile the language server and install it into this plugin:

```bash
./build.sh
```

This builds `spring-boot-language-server-standalone` from source and copies the resulting fat jar to `language-server/`.

## Usage

### Installation

To install the latest release of the plugin from the official Spring Marketplace:

```bash
claude plugin marketplace add https://cdn.spring.io/spring-tools/release/claude-plugins/marketplace.json
claude plugin install spring-boot@spring-official-marketplace
```

To install the bleeding-edge snapshot:
```bash
claude plugin marketplace add https://cdn.spring.io/spring-tools/snapshot/claude-plugins/marketplace.json
claude plugin install spring-boot@spring-official-snapshots
```

### During development / testing

Due to a known bug in Claude Code with the `--plugin-dir` flag, local plugins must be installed via a local marketplace or tarball.

To test your local changes, you can package the plugin and install from a tarball directly:
```bash
./build.sh
tar -czf spring-boot-local.tar.gz .claude-plugin .lsp.json .mcp.json proxy.js language-server README.md
claude plugin install ./spring-boot-local.tar.gz
```

Once installed, just run `claude` normally.

### Updating the plugin

If you make changes to the `.lsp.json` or plugin manifest, you may need to update the installation:

```bash
claude plugin update spring-boot
```

### Testing the LSP Plugin

To verify that the Spring Boot Language Server is correctly booting up and providing diagnostics to Claude Code, you must run Claude Code **interactively** (don't use the `-p` single-shot flag, as it will kill the CLI before the LSP finishes initializing).

Open a Spring Boot project and start Claude Code:
```bash
claude
```

Then, ask Claude to open a file and check the diagnostics. For example:
> "Open CoffeeController.java and tell me what the Spring Boot LSP says about the @GetMapping version attribute."

Claude will wait for the LSP to initialize, read the file, and then summarize the exact Spring Boot warnings and quick fixes provided by the Language Server.

## What the language server provides

- **Diagnostics** — Spring-specific warnings and quick fixes (missing annotations, incorrect bean wiring, etc.)
- **Completions** — Spring Boot properties (`application.properties` / `application.yml`), annotation values, bean references
- **Navigation** — Go to definition for Spring beans, `@Value` expressions, request mappings
- **Inlay hints** — Cron expressions, JPA/JPQL queries, SpEL expressions

## Plugin structure

```
spring-boot/
├── .claude-plugin/
│   └── plugin.json          # Plugin manifest
├── .lsp.json                # LSP server proxy configuration (runs proxy.js)
├── .mcp.json                # MCP server configuration (starts the Java process)
├── proxy.js                 # Node.js script to pipe stdio to the Java LSP socket
├── language-server/         # Populated by build.sh (gitignored)
│   └── spring-boot-language-server-standalone-exec.jar
├── build.sh                 # Build script
└── README.md
```

## How it works

To eliminate race conditions and avoid booting multiple heavy Java processes, this plugin configures Claude Code to share a single JVM for both MCP and LSP:

1. **MCP starts the server:** Claude Code parses `.mcp.json` at startup. This boots the standalone Spring Boot Language Server, instructing it to expose its MCP tools over `stdio` and its LSP over a local TCP socket (port 5007).
2. **LSP connects via proxy:** When you open a relevant file (e.g. `.java`, `.properties`), Claude Code parses `.lsp.json` and starts `proxy.js` as its "LSP process". This lightweight Node.js script simply forwards Claude Code's standard input/output streams to the already-running Java process on port 5007, avoiding the need to spawn a second JVM.

The standalone LS uses `MavenProjectCache` and `GradleProjectCache` to scan the workspace for `pom.xml` and `build.gradle` files, discovering projects without JDT LS. All type indexing is done locally using [Jandex](https://smallrye.io/jandex/).
