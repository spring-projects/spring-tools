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

```bash
claude --plugin-dir ./claude-extensions/spring-boot
```

### Install permanently

Follow the [Claude Code plugin installation guide](https://code.claude.com/docs/en/discover-plugins) to install from a local directory or marketplace.

Once installed, the language server starts automatically when you open `.java`, `.properties`, `.yml`, or `.xml` files in a Spring Boot project. No configuration required.

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
