# Spring Tools Language Server — Claude Code Plugin

A [Claude Code](https://code.claude.com) plugin that contributes the Spring Tools Language Server, exposing Spring Boot diagnostics, bean/request-mapping lookups, and other project insights to Claude Code via MCP tools.

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
claude plugin install spring-tools@spring-tools-marketplace
```

**If you added the snapshot marketplace:**
```bash
claude plugin install spring-tools@spring-tools-snapshots
```

### 3. Update the Plugin

When new versions of the plugin are published to the marketplace, update it by running:

```bash
claude plugin marketplace update
claude plugin update spring-tools
```

### 4. Testing the Plugin

To verify that the Spring Boot Language Server is correctly booting up and serving MCP tools to Claude Code, you must run Claude Code **interactively** (don't use the `-p` single-shot flag, as it will kill the CLI before the language server finishes initializing).

Open a Spring Boot project and start Claude Code:
```bash
claude
```

Then, ask Claude a test query to verify the MCP integration. For example:
> "Check the project diagnostics for CoffeeController.java."

Claude will wait for the language server to initialize, call the `getProjectDiagnostics` MCP tool, and summarize the exact Spring Boot warnings and quick fixes it reports.

### 5. Local Testing

We maintain a local marketplace configuration (`claude-plugins/.claude-plugin/marketplace.json`) to make testing the plugin directly from the source tree easy.

1. Run the update script to build the standalone language server JAR and copy it into this plugin's directory (run this from the `claude-plugins` directory):
   ```bash
   ./update-local-jars.sh
   ```
2. Add the local `claude-plugins` directory as a marketplace (run this from the `sts4` root directory):
   ```bash
   claude plugin marketplace add ./claude-plugins
   ```
3. Install the plugin from your new local marketplace:
   ```bash
   claude plugin install spring-tools@spring-tools-local
   ```

## Configuring language server preferences

You can customize validation severities and other language server settings on a per-project basis by placing a settings file inside the `.claude/` directory of your project. Two formats are supported and may coexist — the properties file provides the base values and the JSON file overrides them.

### Properties format (`.claude/spring-tools.properties`)

Flat `key=value` format where each key is the full dot-separated settings path. This is the simplest format to get started with:

```properties
# Category enablement toggles (AUTO / ON / OFF)
boot-java.validation.java.boot2=OFF
boot-java.validation.java.boot3=ON
boot-java.validation.spel.on=ON
boot-java.validation.java.version-validation=OFF

# Per-problem severity overrides (IGNORE / HINT / INFO / WARNING / ERROR)
spring-boot.ls.problem.boot2.JAVA_PUBLIC_BEAN_METHOD=IGNORE
spring-boot.ls.problem.boot2.JAVA_AUTOWIRED_CONSTRUCTOR=IGNORE
```

### JSON format (`.claude/spring-tools.json`)

Nested JSON matching the VSCode `boot-java` / `spring-boot` configuration structure. Useful when you want to express several settings for the same category together:

```json
{
  "boot-java": {
    "validation": {
      "java": {
        "boot2": "OFF",
        "boot3": "ON"
      },
      "spel": { "on": "ON" },
      "version-validation": { "on": "OFF" }
    }
  },
  "spring-boot": {
    "ls": {
      "problem": {
        "boot2": {
          "JAVA_PUBLIC_BEAN_METHOD": "IGNORE",
          "JAVA_AUTOWIRED_CONSTRUCTOR": "IGNORE"
        }
      }
    }
  }
}
```

### Available settings

**Category enablement toggles** (`boot-java.validation.*`) accept `AUTO`, `ON`, or `OFF`.

**Per-problem severity overrides** (`spring-boot.ls.problem.<category>.<code>`) accept `IGNORE`, `HINT`, `INFO`, `WARNING`, or `ERROR`.

The full list of available categories and problem codes is embedded in the language server JAR as `problem-types.json`. They are the same keys used in the VSCode extension's settings.

Settings are applied once at startup. You must restart the language server (and therefore Claude Code) for changes to take effect.

## What the language server provides

Via MCP tools:

- **Diagnostics** — Spring-specific warnings and quick fixes (missing annotations, incorrect bean wiring, etc.), including version validation results
- **Project insight** — bean, component, and request-mapping lookups; resolved project classpath

Via hooks (`hooks/hooks.json`), the plugin also tracks file and project changes on disk to keep its internal index up to date, and exposes a command to refresh the index manually.

## Plugin structure

```
spring-tools/
├── .claude-plugin/
│   └── plugin.json          # Plugin manifest (MCP server config)
├── launcher.js              # Node.js script that downloads the JAR (if missing) and starts Java
├── install.js               # Node.js script that downloads the JAR
├── hooks/                   # Hooks that notify the language server of file/project changes
├── language-server/         # Populated by install.js on first run (gitignored)
│   └── spring-boot-language-server-standalone-exec.jar
├── skills/                  # Claude Code skills
│   ├── validate/
│   └── quickfix/
├── explanations/            # Markdown files with problem explanations and fixes
└── README.md
```

## How it works

Claude Code parses the MCP configuration in `plugin.json` at startup. This triggers `launcher.js`, which checks if the heavy Java JAR is downloaded. If not, it executes `install.js` to download it from Spring's CDN. Then it boots the standalone Spring Tools Language Server, instructing it to expose its MCP tools over `stdio` (the language server's own LSP socket transport is disabled, since nothing in this plugin connects to it).
