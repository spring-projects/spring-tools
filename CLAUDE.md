# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

Spring Tools (STS4) is a comprehensive IDE tooling suite for Spring Boot development. It provides extensions for Eclipse IDE, Visual Studio Code, and Eclipse Theia. The core architecture uses the Language Server Protocol (LSP): Java language servers run as separate processes, with thin client integrations for each IDE.

## Directory Structure

```
sts4/
├── headless-services/          # Java Language Servers (Maven-based)
│   ├── commons/                # Shared libraries and LSP infrastructure
│   ├── spring-boot-language-server/      # Core Spring Boot language server (with JDT LS)
│   ├── spring-boot-language-server-standalone/  # Standalone variant (Jandex, no JDT LS)
│   ├── manifest-yaml-language-server/   # Cloud Foundry manifest YAML support
│   ├── concourse-language-server/       # Concourse CI pipeline support
│   ├── bosh-language-server/            # BOSH configuration support
│   ├── jdt-ls-extension/       # Eclipse JDT Language Server extensions
│   └── xml-ls-extension/       # XML language server extension
├── eclipse-extensions/         # Eclipse IDE Plugin Extensions (Tycho/Maven)
├── eclipse-language-servers/   # Eclipse LSP4E Integration (Tycho/Maven)
├── eclipse-distribution/       # Pre-packaged Eclipse distribution (Tycho/Maven)
├── vscode-extensions/          # TypeScript VSCode Extensions (npm-based)
│   ├── vscode-spring-boot/     # Main Spring Boot extension
│   ├── vscode-concourse/       # Concourse CI extension
│   ├── vscode-manifest-yaml/   # Cloud Foundry manifest extension
│   ├── vscode-bosh/            # BOSH configuration extension
│   ├── commons-vscode/         # Shared TypeScript utilities
│   └── boot-dev-pack/          # Extension pack bundling Spring Boot extensions
├── nodejs-packages/            # Shared Node.js utilities
└── claude-plugins/             # Claude Code plugin (standalone LS + MCP server)
    ├── spring-tools/           # Plugin source
    └── update-local-jars.sh    # Rebuilds the standalone LS JAR for the plugin
```

## Build System Architecture

The project uses three independent build systems:

### Java Language Servers (Maven)

```bash
cd headless-services
./mvnw clean install                    # Full build with tests
./mvnw clean install -DskipTests=true   # Skip tests
./mvnw clean install -pl spring-boot-language-server -am  # Single module + deps
```

- Parent POM: `headless-services/pom.xml` (aggregator)
- Commons parent: `headless-services/commons/pom.xml` (defines Spring Boot parent, Java 21 source/target)
- CI runs with JDK 25; source/target compatibility is Java 21
- Main entry point: `org.springframework.ide.vscode.boot.app.BootLanguageServerBootApp`

### Eclipse Plugins (Tycho/Maven)

```bash
cd eclipse-language-servers
./mvnw -Pe439 clean install              # Eclipse 2026-03 (4.39)
./mvnw -Pe440 clean install              # Eclipse 2026-06 (4.40)

cd eclipse-distribution
./mvnw -Pe439 -Psnapshot clean package   # Full Eclipse distribution
```

Eclipse profile options: `e439` (2026-03), `e440` (2026-06).

### VSCode Extensions (npm)

Each extension is built independently. The Spring Boot extension build also compiles the language server:

```bash
cd vscode-extensions/vscode-spring-boot
./scripts/preinstall.sh    # First-time: builds commons-vscode and the language server JAR
npm install
npm run compile            # Compile TypeScript
npm run watch              # Watch mode
npm run lint               # ESLint
npm run vsce-package       # Package as .vsix
npm run vsce-pre-release-package
```

`preinstall.sh` packs `commons-vscode` locally, then builds the Spring Boot language server fat JAR and extracts it into `language-server/`.

### Claude Code Plugin

The `claude-plugins/spring-tools` plugin uses the **standalone** language server (no JDT LS, uses Jandex for type indexing):

```bash
cd claude-plugins
./update-local-jars.sh    # Rebuild and copy the standalone LS JAR into the plugin
```

## Running Tests

```bash
# Java: all tests
cd headless-services && ./mvnw test

# Java: single test class
./mvnw test -pl spring-boot-language-server -Dtest=BootLanguageServerTest

# Java: single test method
./mvnw test -pl spring-boot-language-server -Dtest=BootLanguageServerTest#myTestMethod

# VSCode extension tests
cd vscode-extensions/vscode-spring-boot && npm test
```

## Debugging: Connect Local Language Server to IDE

The language server can be started standalone (port 5007) and connected to from either VSCode or Eclipse:

**Start standalone server:**
```bash
cd headless-services
./mvnw clean install -DskipTests -pl spring-boot-language-server -am
java -Dstandalone-startup=true -jar spring-boot-language-server/target/*-exec.jar
```

**Connect VSCode extension:**
1. `cd vscode-extensions/vscode-spring-boot && ./scripts/preinstall.sh && npm install`
2. Open in VSCode: `code .`
3. In `lib/Main.ts`, set `CONNECT_TO_LS: true`
4. Press `F5` to launch the extension workbench — it connects to port 5007

**Connect Eclipse runtime:**
- Import `eclipse-language-servers` projects into Eclipse workspace
- Run Eclipse runtime workbench with VM arg: `-Dboot-java-ls-port=5007`

## Architecture

### Language Server Protocol Flow

```
IDE Client (VSCode/Eclipse) ←—LSP over stdio/socket—→ Java Language Server Process
```

- The full Spring Boot LS (`spring-boot-language-server`) requires JDT Language Server in the same process for Java type resolution
- The standalone variant (`spring-boot-language-server-standalone`) resolves types via Maven/Gradle classpath + Jandex indexing — used by the Claude Code plugin

### Key Commons Modules

| Module | Purpose |
|---|---|
| `commons-language-server` | Base LSP server infrastructure |
| `commons-java` | Java AST parsing and analysis |
| `commons-rewrite` | OpenRewrite recipe integration (quickfixes) |
| `commons-maven` / `commons-gradle` | Project model analysis |
| `commons-yaml` | YAML parsing (shared across YAML-based language servers) |
| `language-server-test-harness` | Testing utilities for LSP servers |
| `jpql` | JPQL query validation |

### Code Transformations (OpenRewrite)

Quick fixes that transform Java source code are implemented as OpenRewrite recipes in `headless-services/commons/commons-rewrite`. Tests live in `commons-rewrite-test`.

## CI/CD Workflows

GitHub Actions workflows in `.github/workflows/`:

- **`snapshot-all.yml`** — Main nightly build: language servers, Eclipse plugins, Eclipse distributions, all VSCode extensions
- **`multiplatform-ls-build.yml`** — Reusable: builds headless-services on Linux/macOS/Windows
- **`eclipse-ls-extensions-build.yml`** — Reusable: builds Eclipse language server plugins with Tycho
- **`build-vscode-extension.yml`** / **`snapshot-vscode-extension.yml`** — Individual VSCode extension builds
- **`release-standalone-ls.yml`** — Publishes standalone LS to CDN (`cdn.spring.io/spring-tools`)

**Version numbers:**
- `headless-services`: `2.3.0-SNAPSHOT` (commons parent POM)
- `eclipse-language-servers` / `eclipse-distribution`: `5.3.0-SNAPSHOT`
- VSCode extensions: `2.3.0` (in `package.json`)

## Contribution Notes

- **License**: Eclipse Public License v1.0 — add EPL header to all new Java files (see `java-copyright-header.txt`)
- **DCO**: Commits must include `Signed-off-by` trailer (no CLA required)
- **HTTPS enforcement**: Run `./nohttp.sh` to verify all URLs in source use HTTPS
- **Commit messages**: Reference issues as `Fixes gh-XXXX`; add `@author` tag to new Java classes
