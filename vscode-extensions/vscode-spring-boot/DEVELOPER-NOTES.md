# Developer notes 

## Bulding and Running

This project consists of three pieces:

 - a vscode-extension which is a language-server client implemented in TypeScript.
 - commons-vscode: a local npm module with some utilities implemented in TypeScript.
 - a language server implemented in Java.

To build all these pieces you normally only need to run:

   npm install

**However, the first time you build** it might fail trying to
find the `commons-vscode` module on npm central. Once we publish a stable 
version of that module on npm central that will no longer be a problem. 
Until that time, you can work around this by doing a one time manual 
run of the `preinstall` script prior to running `npm install`:

    ./scripts/preinstall.sh
    npm install

Now you can open the client-app in vscode. From the root of this project.

    code .

To launch the language server in a vscode runtime, press F5.

## Debugging

### Method 1: attach remote debugger to Language Server

To debug the language server, open `lib/Main.ts` and edit to set the
`DEBUG` option to `true`. When you launch the app next by pressing
`F5` it will launch with debug options being passed to the server JVM.

You can then connect a 'Remote Java' debugger on port 8000.

### Method 2: Launch a 'standalone' Language Server

To debug the language server, open `lib/Main.ts` and edit to set the
`CONNECT_TO_LS` option to `true`. When you launch the app next by pressing
`F5`... When it needs a language server it will not launch a process but instead
try to connect to an already running server on port `5007`. It is up to you
to ensure a server is running on that port by launching it beforehand 
with a commandline arguments: `-Dlanguageserver.standalone=true`.

## Packaging as a vscode extension

First make sure the stuff is all built locally:

     ./scripts/preinstall.sh  # only needed if this is the first build.
     npm install

Then package it:

     npm run vsce-package

This produces a `.vsix` file which you can install directly into vscode.
