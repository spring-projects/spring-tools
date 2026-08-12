# Spring Tools - Dev Environment Setup

## Running the Spring Tools Java Language Server locally

Import the headless-services modules into your workspace as existing Maven projects.
Startup the Boot Language Server with:
Main Class: `org.springframework.ide.vscode.boot.app.BootLanguageServerBootApp` (or a corresponding class for other language servers)
Vmargs: `-Dlanguageserver.standalone=true`
This starts up the language server and it listens on port 5007
Use STS or a dedicated instance of VSCode to launch `org.springframework.ide.vscode.boot.app.BootLanguageServerBootApp` in run or debug mode (or another Language Server app)

@ Connect local Spring Tools Java LS to VSCode

You should run the extension from vscode as a 'runtime workbench' with some small changes.

Start by opening the extension for the server you want to work on in vscode:

```
$ cd vscode-extensions/vscode-spring-boot
$ ./build.sh
$ code .
```

Open `Main.ts` and change:
```
CONNECT_TO_LS: true
```

Save, then launch by pressing `F5`.

This connects the VSCode extension to the locally running language server.
It also uses port 5007

## Connect local Spring Tools Java LS to Eclipse runtime workbench

Startup Eclipse or the Spring Tools for Eclipse distriution. I.e something that support for RCP / plugin development.

Define a recent nightly Spring Tools for Eclipse build as your target platform.

Import the projects under `eclipse-language-servers` into your workspace.

Startup a runtime workbench of the Spring Tools for Eclipse distribution
The language server plugin is: org.springframework.tooling.boot.java.ls
Add Vmargs: `-Dboot-java-ls-port=5007`
This lets the Eclipse language server extension connect to the locally running language server on port 5007

## Additional Resources:

- Generic editor source code (used by lsp4e as a dependency): https://github.com/eclipse/eclipse.platform.text
  Not sure which version of this you should checkout, it probably depends on your version of Eclipse as this is part
  of the eclipse platform. 
- LSP4E source code: clone git repo at https://github.com/eclipse-lsp4e/lsp4e and import the projects into your workspace.
- LSP4J source code: Add the LSP4J-SDK feature to your target platform. We have usually versions from this in our builds:
         http://services.typefox.io/open-source/jenkins/job/lsp4j/job/master/lastStableBuild/artifact/build/p2-repository/

- There is an update site that contains our latest language servers as extensions for Eclipse:
  https://cdn.spring.io/spring-tools/snapshot/TOOLS/sts4-language-server-integrations/nightly
  This is being produced by:
  https://github.com/spring-projects/spring-tools/actions

- CI builds of the full Spring Tools for Eclipse distribution can be downloaded from here:
  https://cdn.spring.io/spring-tools/snapshot/STS4/nightly-distributions.html
