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

### During development / testing

Due to a known bug in Claude Code with the `--plugin-dir` flag, local plugins must be installed via a local marketplace. We have provided a `marketplace.json` in the `claude-plugins` directory for this purpose.

To install it for local testing:

```bash
# From the git root directory:
claude plugin marketplace add ./claude-plugins
claude plugin install spring-boot@spring-tools-local --scope local
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
├── .lsp.json                # LSP server configuration (uses java directly — cross-platform)
├── language-server/         # Populated by build.sh (gitignored)
│   └── spring-boot-language-server-standalone-exec.jar
├── build.sh                 # Build script
└── README.md
```

## How it works

The plugin registers a Language Server Protocol server for Java, Spring Boot properties, and XML files. Claude Code launches the server process when any of these file types are opened and communicates over `stdio`.

The standalone LS uses `MavenProjectCache` and `GradleProjectCache` to scan the workspace for `pom.xml` and `build.gradle` files, discovering projects without JDT LS. All type indexing is done locally using [Jandex](https://smallrye.io/jandex/).
