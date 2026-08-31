# Dummy Java Tooling (Spring Tools test harness)

A throwaway, unpublished VSCode extension that plays the role of a Java tooling
extension (`redhat.java` + `vscjava.vscode-maven`) for `vscode-spring-boot`.

It does not do any real Java tooling. It hard-codes the two messages that a real
Java tooling extension sends to make `vscode-spring-boot` work, each exposed as
its own Command Palette command so you can trigger them one at a time and watch
what happens - nothing is sent automatically on startup:

1. **Dummy Java Tooling: Start Spring Boot Language Server** - sends the client
   command that starts the Boot language server (`vscode-spring-boot.ls.start`
   - normally sent by the JDT LS extension once it detects a Spring Boot
   project).
2. **Dummy Java Tooling: Send Fake Project/Classpath Info** - sends a
   project/classpath discovery event, through the same
   `java.execute.workspaceCommand` bridge that `commons-vscode`
   (`classpath.ts` / `java-data.ts`) already calls. Only works once the Boot LS
   has asked to register a classpath listener (i.e. after the language server
   was started and classpath listening was enabled) - otherwise it shows a
   warning telling you to run the first command. The payload is not
   synthesized - `recorded-classpath-message.json` is a verbatim capture of a
   real `sts4.classpath.*` callback that redhat.java sent for a real
   `spring-petclinic` checkout, and it is replayed byte-for-byte, regardless of
   which folder is actually open in the Extension Development Host.

This lets `vscode-spring-boot` run and index a project with no JDT LS / Maven
extension installed at all, to exercise and demonstrate the project/classpath
discovery protocol described in `JavaProjectsService` /
`ClasspathListenerManager` (see `headless-services/commons/commons-language-server`
and `headless-services/spring-boot-language-server/.../boot/jdt/ls`).

## The recorded message and its placeholders

`recorded-classpath-message.json` holds the 6 positional arguments
(`projectUri`, `name`, `deleted`, `classpath`, `projectBuild`,
`javaCoreOptions`) exactly as captured - 178 classpath entries (6 source
folders + 172 dependency/system jars, most with a `sourceContainerUrl`) and
593 JDT compiler/formatter `javaCoreOptions`.

Three absolute paths from the original recording were specific to the machine
it was captured on, so they've been replaced in the JSON file with literal
placeholder tokens, none of which are fixed constants - all three are
resolved at send time in `extension.js`:

- `__PROJECT_PATH__` wherever the recording referenced the `spring-petclinic`
  checkout root (20 occurrences: `projectUri`, `projectBuild.buildFile`, and
  each of the 6 source classpath entries). Defaults to whichever folder is
  currently open in VSCode (`vscode.workspace.workspaceFolders[0]`), so the
  reported project always matches wherever you actually opened.
- `__JDK_PATH__` wherever it referenced the JDK installation root used to
  resolve `spring-petclinic` (3 occurrences, all on the `jrt-fs.jar` binary
  entry and `classpath.jre.installationPath`). Discovered the same way a real
  Java tooling extension would have to - `getJdkPath()` in `extension.js`
  prefers `JAVA_HOME`, otherwise scans `PATH` for a `java` command and
  resolves it (following symlinks - sdkman/jenv/asdf/homebrew all install
  `java` as one) back to its installation root.
- `__MAVEN_REPO_PATH__` wherever it referenced the local Maven repository
  (341 occurrences, across the `path`/`sourceContainerUrl` of most binary
  classpath entries). Defaults to `~/.m2/repository` via the `MAVEN_REPO_PATH`
  constant at the top of `extension.js` - edit it if yours lives elsewhere.

Everything else in the JSON (the actual GAV/version list, the JDT compiler
options, etc.) stays as originally recorded and is not templated.

## Running it

1. Build `vscode-spring-boot` once so its `language-server/` folder exists:
   ```
   cd ../vscode-spring-boot
   ./scripts/preinstall.sh
   npm install
   ```
   (`redhat.java` no longer needs to be installed for this - its
   `extensionDependencies` entry was removed.)
2. Open this folder (`vscode-dummy-java-tooling`) in VSCode and press `F5`
   (uses `.vscode/launch.json`, which loads both this extension and
   `../vscode-spring-boot` in development mode).
3. In the launched Extension Development Host, open the folder you want
   reported as the project (the recorded message's classpath entries and
   `pom.xml` are all relative to whatever folder you open - it doesn't need to
   be an actual Maven project since none of it is resolved for real), then
   open the Command Palette (`Cmd+Shift+P`) and run, in order:
   1. `Dummy Java Tooling: Start Spring Boot Language Server` - watch for the
      "sent vscode-spring-boot.ls.start" notification, followed shortly by
      "classpath listener registered - run ... Send Fake Project/Classpath
      Info".
   2. `Dummy Java Tooling: Send Fake Project/Classpath Info` - watch for the
      "sent recorded classpath for 'spring-petclinic'" notification.

Running "Send Fake Project/Classpath Info" before the language server has
registered a classpath listener, with no folder open, or with no `java`
command found on `JAVA_HOME`/`PATH`, shows a warning instead of sending
anything.

To see full Spring symbol indexing rather than just the wire protocol, enable
Jandex indexing for whichever folder you open (e.g. via that folder's own
`.vscode/settings.json`):
```json
{
  "spring-boot.ls.java.vmargs": ["-Dlanguageserver.boot.enable-jandex-index=true"]
}
```
Without it, the Boot LS resolves types by calling back into "the Java tooling
extension" (`sts/javaType`, `sts/javaSearchTypes`, etc.), which this dummy
extension only stubs out - so navigation/hover would hang or fail.

## Known limitations

- Only one project/classpath event is sent, once, per command invocation.
  There's no simulated rebuild/dependency-change flow.
- `name` is still the literal recorded `"spring-petclinic"` string regardless
  of which folder is open - only the paths are templated, not the name.
- The recorded GAV/version list itself is not templated, only the Maven
  repository root. On a machine where a given jar version was never resolved
  into that repository, that one classpath entry just won't resolve on disk.
- The `sts.java.*` Java-model query commands (used only when Jandex indexing
  is off) are stubbed to return empty/`null` results, not real data.
- `maven.goal.custom` / `gradle.runBuild` (used by Maven/Gradle quick fixes,
  normally provided by `vscjava.vscode-maven`) are not implemented here.
