---
name: create-spring-boot-project
description: Creates a new Spring Boot project by downloading it from start.spring.io using curl, then extracting it into the target directory. Use this when a user asks to create, generate, initialize, or scaffold a new Spring Boot project.
arguments: [project_description]
allowed-tools: Bash, View
---

You are creating a new Spring Boot project based on the user's request: `$ARGUMENTS[0]`

## Step 1: Detect the available JDK version

Before choosing parameters, detect the major version of the JDK available in the environment, and use it as the default `javaVersion`:

```bash
java -version 2>&1 | head -n 1
```

Parse the major version (e.g. `21`, `17`, `11`, `8`) from the output. If no JDK is available, stop and inform the user.

## Step 2: Gather project parameters

Determine the following from the user's request (use defaults where not specified):

| Parameter      | Default              | Notes                                              |
|----------------|----------------------|----------------------------------------------------|
| `type`         | `maven-project`      | `maven-project` or `gradle-project`                |
| `language`     | `java`               | `java`, `kotlin`, or `groovy`                      |
| `bootVersion`  | *(omit)*             | Omit to use the latest stable version automatically|
| `groupId`      | `com.example`        |                                                    |
| `artifactId`   | `demo`               | Derived from project name if given                 |
| `name`         | same as `artifactId` | Becomes the main class name (`<Name>Application`)  |
| `description`  | `Demo project`       |                                                    |
| `packageName`  | `com.example.demo`   | `groupId` + `.` + `artifactId`                     |
| `packaging`    | `jar`                | `jar` or `war`                                     |
| `javaVersion`  | *(detected in Step 1)* | Override only if user explicitly asked           |
| `dependencies` | *(none)*             | Comma-separated IDs from start.spring.io           |

Note that `name` is also used to derive the main application class file. A hyphenated `artifactId` like `my-service` will produce `MyServiceApplication.java`. If the user wants a specific class name, set `name` independently from `artifactId`.

### Finding Dependency IDs

**Common IDs:**
- **Web**: `web` (Spring Web / REST), `webflux` (Reactive Web), `graphql`, `thymeleaf`
- **Data**: `data-jpa`, `data-mongodb`, `data-redis`, `jdbc`
- **Database drivers**: `h2`, `postgresql`, `mysql`
- **Security**: `security`, `oauth2-client`, `oauth2-resource-server`
- **Messaging**: `amqp` (RabbitMQ), `kafka`, `mail`
- **Validation / DevTools / Ops**: `validation`, `devtools`, `actuator`, `lombok`, `docker-compose`, `testcontainers`
- **Cloud**: `cloud-config-client`, `cloud-eureka` (Eureka **client**), `cloud-eureka-server` (Eureka **server**)
- **AI**: Spring AI dependency IDs (e.g. for OpenAI, Ollama, Anthropic, vector stores) change frequently between Boot versions. ALWAYS verify them via the `/dependencies` endpoint below rather than guessing.

If the user requested a technology not listed above, or you have any doubt about the exact ID, you MUST look up the correct ID by running:

```bash
curl -sS "https://start.spring.io?bootVersion=<bootVersion>" | grep -i -A1 '<search-term>'
```

(If no specific Boot version was requested, omit the `?bootVersion=...` query parameter.)

When called by `curl` without an `Accept: application/json` header, `start.spring.io` returns a human-readable text page that includes a table of every supported dependency with columns `Id`, `Description`, and `Required version`. This is far simpler than parsing the JSON `/dependencies` endpoint — do NOT write a Python/jq/sed script to parse JSON. Just `curl` the URL and `grep` for the technology name (e.g. `kafka`, `openai`, `eureka`). Read the matching `Id` value directly from the table.

The `bootVersion` query parameter is accepted but does not filter rows out of the table; instead, use the `Required version` column on the matched row to confirm the dependency is compatible with the requested Boot version (e.g. `>=3.5.0 and <4.1.0-M1`). If the requested Boot version falls outside that range, do not use the dependency.

## Step 3: Determine the target directory

- If the user specified a path, use that.
- Otherwise, create a new folder named after the `artifactId` in the current working directory.
- If the target directory already exists and is non-empty, STOP and ask the user whether to overwrite, choose a different name, or abort. Do not extract on top of existing files.

## Step 4: Download and extract the project

*Note: The commands below are designed for macOS and Linux-based systems. If you are running on Windows, you MUST adapt these commands appropriately for your environment (e.g., downloading a `.zip` to a temporary file and extracting it using PowerShell, rather than piping binary data). Note that older Windows builds may ship a `tar` that does not support `-z`; `bsdtar` from Windows 10+ does.*

Use a `POST` request to `start.spring.io/starter.tgz` and pipe it directly into `tar`. This avoids URL encoding issues for the URL itself and avoids temporary files.

Run the following command. Add `-d bootVersion=<bootVersion>` only if the user explicitly requested a Boot version, and omit any other `-d` parameters that are not specified:

```bash
set -o pipefail
mkdir -p <target-directory>
curl -fsS https://start.spring.io/starter.tgz \
  -d type=<type> \
  -d language=<language> \
  -d groupId=<groupId> \
  -d artifactId=<artifactId> \
  --data-urlencode "name=<name>" \
  --data-urlencode "description=<description>" \
  -d packageName=<packageName> \
  -d packaging=<packaging> \
  -d javaVersion=<javaVersion> \
  -d dependencies=<dep1,dep2,...> | tar -xzf - -C <target-directory>
```

Use `--data-urlencode` for any free-text field that may contain spaces or special characters (`name`, `description`). Plain `-d` does NOT URL-encode values, which can corrupt the request body. The `-f` flag on `curl` makes HTTP errors (e.g. 400 from start.spring.io for an invalid dependency ID) return a non-zero exit code instead of streaming an error body into `tar`. Combined with `set -o pipefail`, this surfaces the real failure cause.

## Step 5: Confirm success

After extraction, verify the project was created correctly by checking for a build file:

```bash
ls -la <target-directory>
test -f <target-directory>/pom.xml \
  || test -f <target-directory>/build.gradle \
  || test -f <target-directory>/build.gradle.kts
```

If no build file is present, treat the operation as failed and report the issue (typically a curl/tar pipeline error from Step 4) instead of claiming success.

On rare environments, the Maven/Gradle wrapper scripts may not retain their executable bit after extraction. If `./mvnw` or `./gradlew` reports "permission denied", run `chmod +x mvnw gradlew` inside the project directory.

## Step 6: Switch the working directory into the new project

Once Step 5 confirms success, change the shell's working directory into the newly created project directory so that any subsequent commands (build, run, edits, follow-up tool calls) operate inside the project:

```bash
cd <target-directory>
pwd
```

Run `pwd` afterwards and confirm the output matches the expected project directory. This `cd` MUST happen as the final action of the skill (after success verification, before reporting back to the user) so that the user's next instruction starts inside the project.

If you also have access to a tool that changes Claude Code's persistent working directory (e.g. a dedicated `cwd` / project-root tool exposed by the host), use it in addition to the shell `cd` so that all tools — not just `Bash` — operate from the new location.

## Step 7: Report back to the user

Report:
- The directory where the project was extracted (and confirm that the working directory is now set to it)
- The selected dependencies
- The resolved `javaVersion` (and how it was detected)
- Any next steps (e.g., `./mvnw spring-boot:run` or `./gradlew bootRun` — note that `cd` is no longer needed because Step 6 already moved into the project)
