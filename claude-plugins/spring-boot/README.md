# Spring Boot Language Server — Claude Code Plugin

A [Claude Code](https://code.claude.com) plugin that contributes the Spring Boot Language Server, providing real-time diagnostics, completions, and navigation for Spring Boot projects.

Unlike the VS Code extension, this plugin uses the **standalone** variant of the language server which operates **without** JDT Language Server. Project classpath is computed directly via Maven and Gradle tooling; type indexing uses Jandex.

## Requirements

- Java 21+ on `PATH`
- Maven or Gradle projects in your workspace

## Usage

### 1. Add the Marketplace

First, add either the Release or Snapshot marketplace to Claude Code:

**To use the stable release:**
```bash
claude plugin marketplace add https://cdn.spring.io/spring-tools/release/claude-plugins/marketplace.json
```

**To use the bleeding-edge snapshot:**
```bash
claude plugin marketplace add https://cdn.spring.io/spring-tools/snapshot/claude-plugins/marketplace.json
```

### 2. Install the Plugin

Once the marketplace is added, install the plugin:

**If you added the stable release marketplace:**
```bash
claude plugin install spring-boot@spring-tools-marketplace
```

**If you added the snapshot marketplace:**
```bash
claude plugin install spring-boot@spring-tools-snapshots
```

### 3. Update the Plugin

When new versions of the plugin are published to the marketplace, update it by running:

```bash
claude plugin marketplace update
claude plugin update spring-boot
```

### 4. Testing the LSP Plugin

To verify that the Spring Boot Language Server is correctly booting up and providing diagnostics to Claude Code, you must run Claude Code **interactively** (don't use the `-p` single-shot flag, as it will kill the CLI before the LSP finishes initializing).

Open a Spring Boot project and start Claude Code:
```bash
claude
```

Then, ask Claude a test query to verify the LSP integration. For example:
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
│   └── plugin.json          # Plugin manifest (includes MCP and LSP server configs)
├── proxy.js                 # Node.js script to pipe stdio to the Java LSP socket
├── launcher.js              # Node.js script that downloads the JAR (if missing) and starts Java
├── install.js               # Node.js script that downloads the JAR
├── language-server/         # Populated by install.js on first run (gitignored)
│   └── spring-boot-language-server-standalone-exec.jar
└── README.md
```

## How it works

To eliminate race conditions and avoid booting multiple heavy Java processes, this plugin configures Claude Code to share a single JVM for both MCP and LSP:

1. **MCP starts the server:** Claude Code parses the MCP configuration in `plugin.json` at startup. This triggers `launcher.js`, which checks if the heavy Java JAR is downloaded. If not, it executes `install.js` to download it from Spring's CDN. Then it boots the standalone Spring Boot Language Server, instructing it to expose its MCP tools over `stdio` and its LSP over a local TCP socket (port 5007).
2. **LSP connects via proxy:** When you open a relevant file (e.g. `.java`, `.properties`), Claude Code parses the LSP configuration in `plugin.json` and starts `proxy.js` as its "LSP process". This lightweight Node.js script simply forwards Claude Code's standard input/output streams to the already-running Java process on port 5007, avoiding the need to spawn a second JVM.
