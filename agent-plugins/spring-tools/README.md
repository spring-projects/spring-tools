# Spring Tools Language Server — Agent Plugin

An agent plugin that contributes the Spring Tools Language Server, exposing Spring Boot diagnostics, bean/request-mapping lookups and other project insights as MCP tools, skills, hooks and a review subagent. The portable [Agent Plugins 1.0](https://agent-plugins.org) package (`plugin.json`, `mcp.json`, and `skills/`) is supported by GitHub Copilot CLI and Codex. Claude Code also reads its native `.claude-plugin/` manifest and `hooks/`; Copilot has its own hook and agent adapters under `com.github.copilot/`. OpenCode can use the same language server over MCP and discovers shared skills through its Agent Skills-compatible `.agents/skills/` path.

Unlike the VS Code extension, this plugin uses the **standalone** variant of the language server which operates **without** JDT Language Server. Project classpath is computed directly via Maven and Gradle tooling; type indexing uses Jandex.

## Requirements

- Java 21+ — found via `SPRING_TOOLS_JAVA`, then `$JAVA_HOME/bin/java`, then `java` on `PATH` (the first candidate that reports Java 21 or newer is used)
- Maven or Gradle projects in your workspace
- Claude Code, GitHub Copilot CLI 1.0.83+, Codex CLI, or OpenCode

Optional environment variables:

| Variable | Purpose |
|---|---|
| `SPRING_TOOLS_JAVA` | Path of the `java` executable to run the language server with |
| `SPRING_TOOLS_JAVA_OPTS` | Extra JVM options, e.g. `-Xmx2g`; appended after the defaults so they take precedence |
| `SPRING_TOOLS_LS_JAR` | Run a local language server JAR instead of the downloaded one (no download happens) |
| `SPRING_TOOLS_SOURCE_DIR` | Spring Tools repository root to use for a local Maven build if downloading the language server fails |
| `SPRING_TOOLS_PROJECT_DIR` | The directory to index, overriding the workspace the agent reports (see [How it works](#how-it-works)) |
| `SPRING_TOOLS_DATA_DIR` | Persistent location for the language-server log and runtime files; the downloaded JAR is stored in a subdirectory for the current plugin version. Defaults to `~/.spring-tools/data` when the host provides no plugin-data directory |
| `SPRING_TOOLS_LS_WATCH` | Set to `false` to turn off the language server's own file watcher (see [What the language server provides](#what-the-language-server-provides)); only Claude's MCP hooks provide a synchronous change-notification fast path |
| `HTTPS_PROXY` / `NO_PROXY` | The JAR download honors the usual proxy variables (`HTTPS_PROXY`, `ALL_PROXY`, `NO_PROXY`, lower-case variants; HTTP proxies with optional basic auth) |
| `MCP_TIMEOUT` | Claude Code's MCP startup timeout in ms; raise it if the language server is reported as failed on a slow machine |

## Installation in Claude Code

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

Claude calls the `getProjectDiagnostics` MCP tool and summarizes the exact Spring Boot warnings and quick fixes it reports. Note that the MCP server is available within a few seconds, but the project itself only shows up in `getProjectList` once the language server has resolved the Maven/Gradle project model - a few seconds with a warm dependency cache, up to a couple of minutes on a cold one. The `validate` skill retries `getProjectList` while the list is still empty.

The published marketplace URLs retain the historical `claude-plugins` path for compatibility. The shared plugin source lives under `agent-plugins/` in this repository.

### 5. Local Testing

We maintain a local marketplace configuration (`agent-plugins/.claude-plugin/marketplace.json`) to make testing the plugin directly from the source tree easy.

1. Run the update script to build the standalone language server JAR and copy it into the source plugin directory for local development (run this from the `agent-plugins` directory):
   ```bash
   ./update-local-jars.sh
   ```
2. Add the local `agent-plugins` directory as a marketplace (run this from the repository root):
   ```bash
   claude plugin marketplace add ./agent-plugins
   ```
3. Install the plugin from your new local marketplace:
   ```bash
   claude plugin install spring-tools@spring-tools-local
   ```

4. Start Claude Code with the locally built JAR selected explicitly (run from the repository root):
   ```bash
   SPRING_TOOLS_LS_JAR="$PWD/agent-plugins/spring-tools/language-server/spring-boot-language-server-standalone-exec.jar" claude
   ```
   Normal installs keep the downloaded JAR in persistent, versioned plugin data; this environment override is only for local development and avoids downloading the published JAR.

## Codex and GitHub Copilot CLI

The plugin’s `plugin.json` and `mcp.json` form the portable Agent Plugins package. This repository includes a Codex marketplace catalog at [`.agents/plugins/marketplace.json`](../../.agents/plugins/marketplace.json). Register the checkout once, then install the plugin:

```bash
codex plugin marketplace add /path/to/spring-tools
codex plugin add spring-tools@spring-tools-local
```

Use `/plugins` in Codex to manage the installed plugin. GitHub Copilot CLI can install directly from this repository's plugin subdirectory:

```bash
copilot plugin install spring-projects/spring-tools:agent-plugins/spring-tools
```

Both hosts get the shared `skills/` and launch the same `launcher.js`. The host-specific `com.github.copilot/` directory supplies Copilot's agent front matter and session-start hook. Its reviewer limits tools to read/search and this plugin's MCP server. The root Agent Plugins files are also the Codex entry point, so those capabilities do not need a second copy.

## OpenCode

OpenCode does not load Agent Plugins packages directly. Its ready-to-merge MCP config is [`opencode/opencode.jsonc`](opencode/opencode.jsonc), and its read-only reviewer adapter is in [`opencode/agents/spring-reviewer.md`](opencode/agents/spring-reviewer.md). Set `SPRING_TOOLS_PLUGIN_ROOT` to the installed plugin directory, then merge the config fragment into your existing `opencode.json` or `opencode.jsonc`:

```jsonc
"mcp": {
  "spring-tools": {
    "type": "local",
    "command": ["node", "{env:SPRING_TOOLS_PLUGIN_ROOT}/launcher.js"],
    "enabled": true,
    "timeout": 120000
  }
}
```

OpenCode discovers skills under `.agents/skills/` and agents under `.opencode/agents/`. From the workspace root, copy the plugin's shared skills and reviewer adapter into those locations:

```sh
mkdir -p .agents/skills .opencode/agents
cp -R "$SPRING_TOOLS_PLUGIN_ROOT/skills/." .agents/skills/
cp "$SPRING_TOOLS_PLUGIN_ROOT/opencode/agents/spring-reviewer.md" .opencode/agents/
```

Invoke the reviewer with `@spring-reviewer`; the shared `spring-review` skill is available to the primary agent too. Set `SPRING_TOOLS_PROJECT_DIR` if OpenCode's MCP process working directory is not the project to index. OpenCode relies on the language-server watcher; it cannot use Claude's MCP-tool hooks. Diagnostic playbooks live beside `launcher.js` in `explanations/` and are named in the MCP server instructions.

## Configuring language server preferences

You can customize validation severities and other language server settings on a per-project basis by placing a settings file inside the `.claude/` directory of your project. Two formats are supported and may coexist — the properties file provides the base values and the JSON file overrides them.

### Properties format (`.claude/spring-tools.properties`)

Flat `key=value` format where each key is the full dot-separated settings path. This is the simplest format to get started with:

```properties
# Category enablement toggles (AUTO / ON / OFF)
boot-java.validation.java.boot2=OFF
boot-java.validation.java.boot3=AUTO
boot-java.validation.java.boot4=ON
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
        "boot3": "AUTO",
        "boot4": "ON",
        "version-validation": "OFF"
      },
      "spel": { "on": "ON" }
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

**Category enablement toggles** (`boot-java.validation.*`) accept `AUTO`, `ON`, or `OFF`. `AUTO` (the default for the `boot2`/`boot3`/`boot4` categories) applies a category only when the project's Spring Boot version matches it, `ON` forces it on for every Spring Boot project regardless of version (for example to see Boot 4 findings while still on Boot 3), and `OFF` disables it. The `spring-aot`, `spel`, `version-validation`, `data-query`, `cron` and `spring-ai` categories only support `ON`/`OFF`; the `application-properties` and `application-yaml` categories have no toggle — silence individual codes with a severity of `IGNORE` instead.

**Per-problem severity overrides** (`spring-boot.ls.problem.<category>.<code>`) accept `IGNORE`, `HINT`, `INFO`, `WARNING`, or `ERROR`.

**Per-problem parameters** (`spring-boot.ls.problem-parameters.<category>.<code>.<key>`) tune individual checks, for example `spring-boot.ls.problem-parameters.spring-ai.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT.minimum-length=40`; the version-validation category has a category-wide parameter `spring-boot.ls.problem-parameters.version-validation.use-project-build-file=false` (look up available versions on spring.io instead of the project's Maven repositories).

The full list of available categories, problem codes and parameters is embedded in the language server JAR as `problem-types.json` (inside the nested `spring-boot-language-server-*.jar`). They are the same keys used in the VSCode extension's settings, so the `boot-java.*` / `spring-boot.*` entries of `vscode-extensions/vscode-spring-boot/package.json` double as a reference.

Settings are applied once at startup. You must restart the language server for changes to take effect: restart Claude Code, or reconnect the `spring-tools-mcp` server from the `/mcp` menu.

## What the language server provides

Via MCP tools:

- **Diagnostics** — Spring-specific warnings and quick fixes (missing annotations, incorrect bean wiring, etc.), including version validation results and checks of `application.properties` / `application.yml` / `META-INF/spring.factories` (unknown or deprecated properties, type mismatches, structural errors, unsupported factories keys). Config files under test resources are only included when `boot-java.scan-java-test-sources.on=true` is set (see below)
- **Project insight** — bean, component, and request-mapping lookups; logical structure and architectural change tracking; resolved project classpath; Java and Spring Boot version; release and support information for Spring projects

Skills are available by name to Codex, Copilot CLI and OpenCode, and are namespaced as `/spring-tools:<name>` in Claude Code. Hosts can also select them automatically when relevant:

- **`validate`** — collects the Spring Tools diagnostics for a project and drives the fixes
- **`quickfix`** — looks up the explanation and fix instructions for a diagnostic code in `explanations/` and applies them
- **`create-spring-boot-project`** — scaffolds a new project from start.spring.io
- **`beans`** — bean definitions, injection points and usages ("where is this bean injected?", "which beans implement X?")
- **`endpoints`** — request mappings, optionally filtered by HTTP method ("which endpoints exist?")
- **`architecture`** — logical structure and stereotypes; captures a baseline and reports the structural changes since
- **`spring-versions`** — the Spring Boot version in use versus the latest releases, support windows and upcoming releases
- **`project-info`** — project list, Java version, Boot version and resolved classpath ("is this project indexed?", "which dependency provides X?")
- **`refresh`** — forces the language server to re-index the workspace from disk (or a single file) when something was missed

Agents (`agents/`) — the plugin also ships a reviewer subagent:

- **`spring-reviewer`** — a read-only Spring review of the current changes: it combines the `getProjectDiagnostics` results with the bean, endpoint and structure tools and reports blocking issues, recommendations and notes without editing anything. Claude Code and Copilot CLI ship dedicated reviewer agents; Codex gets the shared `spring-review` skill; OpenCode can install its reviewer adapter as described above. Invoke the Copilot agent by its file-derived ID: `copilot --agent spring-reviewer`.

The language server keeps its index current in two ways. It **watches** the workspace directory itself and re-indexes changed Java/Kotlin/Groovy, config and build files after a short quiet period — so edits made by an external editor, a code generator or a shell command show up on their own, as do Maven/Gradle projects created after the server started (e.g. by the `create-spring-boot-project` skill). Extra roots added with `/add-dir` are not watched, and neither are edits outside the project root. Set `SPRING_TOOLS_LS_WATCH=false` to turn this off, e.g. for a huge monorepo or a workspace on a network drive; the watcher also disables itself above 20,000 directories. In addition, hooks give the server a synchronous fast path for the agent's own actions: the file-change hooks fire after `Edit`/`Write` tool calls on Java/Kotlin/Groovy source files and build/config files (`.java`, `.kt`, `.kts`, `.groovy`, `.xml`, `.properties`, `.yml`, `.yaml`, `.factories`, `.gradle`) — edits to unrelated files don't trigger them — and the workspace-refresh hook fires after shell commands that change files without going through the file tools: `git`, `rm`, `mv`, `cp`, `sed`, `patch`, `tar`, `unzip` (and `git`, `Remove-Item`, `Move-Item`, `Copy-Item`, `Expand-Archive`, `tar` in PowerShell). A session-start hook runs `install.js --if-missing` so the language server JAR is downloaded before the MCP server needs it. Claude Code reads these from `hooks/hooks.json`; Copilot CLI reads `com.github.copilot/hooks/hooks.json`, which only contains the session-start download: Copilot hooks run shell commands and have no way to call a tool of a running MCP server, so there the file watcher alone keeps the index current.

## Troubleshooting

- **`spring-tools-mcp` shows as failed in `/mcp`** — on the very first start the ~100 MB JAR has to be downloaded; if that takes longer than Claude Code's MCP startup timeout the server is marked failed. Reconnect it from `/mcp` (the download continues/completes in the background) or start Claude Code again; raise `MCP_TIMEOUT` on slow connections.
- **`No Java 21+ runtime found`** on stderr — the launcher lists every candidate it tried with the version it found. Install a JDK 21+ and expose it via `PATH`, `JAVA_HOME` or `SPRING_TOOLS_JAVA`.
- **`getProjectList` is empty** — the Maven/Gradle model is still resolving (up to a couple of minutes on a cold dependency cache); the `validate` skill retries. Use the `project-info` skill to check it; if a project still doesn't appear (for example it lives outside the host's workspace), use the `refresh` skill. In Claude Code, the corresponding commands are `/spring-tools:project-info`, `/add-dir`, and `/spring-tools:refresh`.
- **Changes made outside the agent aren't picked up** — the watcher logs `file watcher started for <dir>` in `boot-ls.log`; if it logs `not started` instead (too many directories, unsupported file system, `SPRING_TOOLS_LS_WATCH=false`), use the `refresh` skill after external changes. Claude Code's shortcut is `/spring-tools:refresh`.
- **Download blocked by a corporate proxy** — set `HTTPS_PROXY`; the installer only downloads from `cdn.spring.io` over HTTPS and verifies the SHA-256 published next to the JAR. If download fails and a Spring Tools source checkout is available, the installer builds the standalone server with its Maven wrapper. For an installed plugin, set `SPRING_TOOLS_SOURCE_DIR` to the repository root; otherwise download the JAR yourself and point `SPRING_TOOLS_LS_JAR` at it.
- **Logs** — `boot-ls.log` in `SPRING_TOOLS_DATA_DIR`, the host's plugin-data directory, or `~/.spring-tools/data`.

## Explanation playbooks

Every diagnostic the language server can report through `getProjectDiagnostics` carries a `code` (for example `JAVA_PUBLIC_BEAN_METHOD`). For each code there is one Markdown playbook `explanations/<CODE>.md` that the `quickfix` skill reads before touching the user's code. A playbook has two sections:

- `## Explanations` — what the diagnostic flags, why it matters, when the language server raises it (Spring/Boot version range, required dependency, what exactly is inspected), and a `For more details, see:` list of official documentation links.
- `## Fixes` — one or more `**Fix N: …**` blocks with prose and *Before:* / *After:* code blocks. Cross-references to other diagnostics are written as backticked codes (for example ``see `JAVA_LAMBDA_DSL` ``) and must point to an existing playbook.

The codes come from the `*ProblemType` enums of the language server (`Boot2JavaProblemType`, `Boot3JavaProblemType`, `Boot4JavaProblemType`, `SpringAotJavaProblemType`, `SpringAiProblemType`, `SpelProblemType`, `cron/CronProblemType`, `data/jpa/queries/QueryProblemType`, `properties/reconcile/ApplicationPropertiesProblemType`, `yaml/reconcile/ApplicationYamlProblemType`) plus two codes that are not enum constants: `BOOT_VERSION_VALIDATION_CODE`, which all Spring Boot version-validation diagnostics share, and `YamlSchemaProblem`, which the YAML reconciler inherits from `commons-yaml` for malformed `<<` merge keys. Coverage is tracked in [`research-notes/TODO_quickfixes.md`](../research-notes/TODO_quickfixes.md).

### Keeping the playbooks consistent

`agent-plugins/tools/` contains a Node.js checker and tests (Node 20+, no dependencies) that keep the playbooks and the plugin configuration in sync with the language server. Run them from the repository root:

```bash
# every code has a playbook, no orphan files, structure/cross-references valid, TODO table in sync
node agent-plugins/tools/check-explanations.mjs --require-all --todo

# additionally verify that every documentation link returns HTTP 200
node agent-plugins/tools/check-explanations.mjs --require-all --links

# only a subset (e.g. while writing a new playbook)
node agent-plugins/tools/check-explanations.mjs --codes JAVA_PUBLIC_BEAN_METHOD,JAVA_LAMBDA_DSL --links

# Run the offline playbook and plugin configuration checks together
npm --prefix agent-plugins/tools run check

# hooks, skills and agents reference only MCP tools the language server offers, every tool is reachable
# through a skill or hook, skills grant exactly the tools they document, the eval suite mocks every tool
node agent-plugins/tools/check-plugin-config.mjs

# tests: checker behaviour (incl. negative-control fixtures), hooks/skills/agents/manifest consistency with the MCP tools,
# installer (against a local mock CDN) and launcher (Java selection, JVM arguments, server instructions)
node --test --test-reporter=spec 'agent-plugins/tools/test/*.test.mjs'

# end-to-end: start launcher.js over MCP stdio like Claude Code does, index a sample project, exercise the
# diagnostics/bean/endpoint/structure tools, edit files with and without notifying the server (file watcher),
# create a project after startup and verify it is discovered (needs the JAR + Java 21+)
node agent-plugins/tools/smoke/mcp-smoke.mjs
```

The GitHub Actions workflow `.github/workflows/agent-plugin-check.yml` runs the same checks for changes to `.agents/plugins/marketplace.json`, under `agent-plugins/`, the `*ProblemType` enums, the MCP tool sources and the standalone language server, validates the manifest with `claude plugin validate --strict`, and runs the smoke test against a freshly built language server.

When a new MCP tool is added to the language server, give it a home: mention it in the skill that covers its use case (its `allowed-tools` list and body) or in `hooks/hooks.json`; `check-plugin-config.mjs` fails until every tool is reachable through a skill or hook.

### Behavioral evals

`evals/` holds a [`claude plugin eval`](https://code.claude.com/docs/en/plugin-evals) suite: one case per skill and for the `spring-reviewer` agent, plus a negative control (an unrelated question must not trigger any skill). Each case is a prompt phrased the way a user would type it, with graders for the result (a regex or a judged rubric) and for the path (`tool_used` on the skill and on the MCP tool it should call). The language server is not started during a run: `evals/mocks/spring-tools-mcp/` answers every MCP tool from recordings taken against the real server on the `sf7-validation` test project (`_tools.json` carries the real tool descriptions and schemas), and the `validate` and reviewer cases override `getProjectDiagnostics` with a recording that contains a `JAVA_AUTOWIRED_CONSTRUCTOR` finding.

```bash
# runs every case three times with the plugin and three times without it, judged with your Claude Code credentials
cd agent-plugins/spring-tools && claude plugin eval . --no-publish

# one case, one arm, one run - cheap while iterating on a skill description
claude plugin eval . --case validate-finds-spring-problems --runs 1 --ablation none

# re-record the mocks after the language server's tools or the fixture project changed (needs the JAR + Java 21+)
node agent-plugins/tools/evals/record-mocks.mjs
```

Every run and every `llm` grader is a model call on your account (Claude Code 2.1.269+, logged in or `ANTHROPIC_API_KEY`). `check-plugin-config.mjs` keeps the suite consistent offline: every tool has a mock, `_tools.json` matches the server's tool list, graders reference only existing tools/skills/agents, and every skill and agent has a case. The `create-project-routes-to-skill` case checks routing only — eval runs don't grant `Bash`, so the skill can't download from start.spring.io there.

When a new diagnostic code is added to the language server:

1. Create `explanations/<CODE>.md` following the structure above; verify every fact against the reconciler that raises the code and against the current Spring documentation, and prefer the current API (state deprecation/removal versions when showing an older one).
2. Add a row to the coverage table in `research-notes/TODO_quickfixes.md`.
3. Run the checker with `--require-all --todo --links` until it passes.

## Plugin structure

```
spring-tools/
├── .claude-plugin/
│   └── plugin.json          # Claude Code manifest (metadata + MCP server config + hooks)
├── plugin.json              # Agent Plugins 1.0 manifest (metadata), used by Copilot CLI and Codex
├── mcp.json                 # Agent Plugins 1.0 MCP server config, used by Copilot CLI and Codex
├── com.github.copilot/      # Copilot-specific extensions (client namespace of the Agent Plugins spec)
│   ├── agents/
│   │   └── spring-reviewer.agent.md   # Same agent, front matter Copilot understands
│   └── hooks/
│       └── hooks.json       # sessionStart JAR download
├── launcher.js              # Node.js script that picks a Java 21+ runtime, downloads the JAR (if missing) and starts it
├── install.js               # Node.js script that downloads and verifies the JAR (also run by the session-start hook)
├── hooks/
│   └── hooks.json           # Claude Code hooks: SessionStart JAR download + notifications about Claude's file/project changes
├── agents/
│   └── spring-reviewer.md   # Read-only Spring review subagent built on the MCP tools
├── skills/                  # Portable shared skills (namespaced as /spring-tools:<name> in Claude Code)
│   ├── validate/
│   ├── quickfix/
│   ├── create-spring-boot-project/
│   ├── beans/
│   ├── endpoints/
│   ├── architecture/
│   ├── spring-versions/
│   ├── project-info/
│   ├── refresh/
│   └── spring-review/       # Canonical review procedure for Codex and other skill hosts
├── opencode/                # OpenCode MCP config fragment and reviewer agent adapter
├── explanations/            # One Markdown playbook per diagnostic code (explanation + fixes)
├── evals/                   # Behavioral eval suite: one case per skill/agent + recorded MCP mocks (results/ is gitignored)
└── README.md
```

The manifests describe one plugin for different hosts. `install.js` stores the checksum-verified JAR in the persistent data directory, isolated under the plugin version; it does not write into the installed plugin tree. `check-plugin-config.mjs` fails if shared metadata drifts, if a reviewer adapter differs from `skills/spring-review/SKILL.md`, or if `mcp.json` stops launching `launcher.js`. Run `node agent-plugins/tools/sync-agent-adapters.mjs --write` after changing the shared review procedure to refresh Claude, Copilot and OpenCode adapters. Hooks cannot be one shared file: Claude supports MCP-tool hooks, Copilot supports command hooks, and Codex/OpenCode rely on the language-server watcher. Codex and Copilot consume the portable root `plugin.json`, `mcp.json`, and `skills/`, so those capabilities are defined once.

The sibling directory `agent-plugins/tools/` (not part of the published plugin) holds `check-explanations.mjs`, `check-plugin-config.mjs`, `lib/plugin-config.mjs`, the `test/` suite, the `smoke/mcp-smoke.mjs` end-to-end test, the `evals/record-mocks.mjs` recorder and the `evals/run-with-copilot.mjs` runner described above.

## How it works

The agent parses the MCP configuration of the plugin at startup (`.claude-plugin/plugin.json` in Claude Code, portable `mcp.json` in Copilot CLI and Codex, or the OpenCode fragment). This triggers `launcher.js`, which selects a Java 21+ runtime and checks if the heavy Java JAR is downloaded. If not, it downloads it from Spring's CDN (`install.js`, SHA-256 verified, written atomically; normally a session-start hook has already done this). Then it boots the standalone Spring Tools Language Server, instructing it to expose its MCP tools over `stdio` and passing server instructions that tell the agent which tools to start with and where the explanation playbooks live.

The directory the server indexes is resolved in this order: `SPRING_TOOLS_PROJECT_DIR`, then `CLAUDE_PROJECT_DIR` (Claude Code), then the workspace Copilot records for the session in `~/.copilot/session-state/<session>/workspace.yaml`, then `PWD`, then the working directory the MCP server was started in. An invalid explicit override is an error. If none of the detected paths identifies a directory outside the plugin root, the launcher stops with configuration instructions. The chosen directory and its source are logged. The server watches that directory for changes unless `SPRING_TOOLS_LS_WATCH=false`. Logs go to `SPRING_TOOLS_DATA_DIR`, the host's plugin-data directory, or `~/.spring-tools/data`.

The standalone server uses the resolved project directory for project discovery, settings-file loading, and file watching. Watcher behavior can be configured with `languageserver.standalone.filewatcher.enabled`, `languageserver.standalone.filewatcher.debounce-ms`, and `languageserver.standalone.filewatcher.max-directories`.
