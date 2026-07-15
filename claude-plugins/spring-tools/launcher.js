const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');

const jarDir = path.join(__dirname, 'language-server');
const jarPath = path.join(jarDir, 'spring-boot-language-server-standalone-exec.jar');

async function start() {
    // 1. Download the JAR if it doesn't exist
    if (!fs.existsSync(jarPath)) {
        console.error("Spring Boot Language Server JAR not found. Downloading on first run...");
        try {
            spawnSync('node', [path.join(__dirname, 'install.js')], { stdio: 'inherit' });
        } catch (e) {
            console.error("Failed to download JAR:", e);
            process.exit(1);
        }
    }

    // 2. Launch the Java process. The plugin only exposes MCP tools over stdio -
    // the LSP socket transport is disabled since nothing connects to it.
    const javaArgs = [
        "-Xmx1024m",
        "-Djdk.util.zip.disableZip64ExtraFieldValidation=true",
        "-Dspring.config.location=classpath:/application.properties",
        "-Dspring.profiles.active=file-logging",
        `-Dlogging.file.name=${path.join(__dirname, 'boot-ls.log')}`,
        "-Dlogging.level.root=INFO",
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dlanguageserver.enabled=false",
        `-Dspring.boot.ls.project.dir=${process.cwd()}`,
        "-jar",
        jarPath
    ];

    const child = spawn('java', javaArgs, { stdio: 'inherit' });

    // When Claude Code exits, it sends a termination signal to this Node script.
    // We must catch these signals and manually kill the heavy Java child process
    // so it doesn't remain active as an orphan in the background.
    ['SIGINT', 'SIGTERM', 'SIGQUIT'].forEach(signal => {
        process.on(signal, () => {
            if (!child.killed) {
                child.kill('SIGTERM');
            }
            process.exit(0);
        });
    });

    child.on('error', (err) => {
        console.error('Failed to start Java process:', err);
        process.exit(1);
    });

    child.on('close', (code) => {
        process.exit(code);
    });
}

start().catch(err => {
    console.error("Failed to start launcher:", err);
    process.exit(1);
});