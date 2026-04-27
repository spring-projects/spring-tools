const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');
const net = require('net');
const common = require('./common');

const jarDir = path.join(__dirname, 'language-server');
const jarPath = path.join(jarDir, 'spring-boot-language-server-standalone-exec.jar');

function getFreePort() {
    return new Promise((resolve, reject) => {
        const srv = net.createServer();
        srv.listen(0, () => {
            const port = srv.address().port;
            srv.close((err) => {
                if (err) reject(err);
                else resolve(port);
            });
        });
        srv.on('error', reject);
    });
}

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

    // 2. Find a free port and save it for the proxy
    const port = await getFreePort();
    fs.writeFileSync(common.portFile, port.toString(), 'utf8');

    // 3. Launch the Java process
    const javaArgs = [
        "-Xmx1024m",
        "-Djdk.util.zip.disableZip64ExtraFieldValidation=true",
        "-Dspring.config.location=classpath:/application.properties",
        "-Dspring.profiles.active=file-logging",
        `-Dlogging.file.name=${path.join(__dirname, 'boot-ls.log')}`,
        "-Dlogging.level.root=INFO",
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dlanguageserver.standalone=true",
        `-Dlanguageserver.standalone-port=${port}`,
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
            if (fs.existsSync(common.portFile)) {
                fs.unlinkSync(common.portFile);
            }
            process.exit(0);
        });
    });

    child.on('error', (err) => {
        console.error('Failed to start Java process:', err);
        if (fs.existsSync(common.portFile)) fs.unlinkSync(common.portFile);
        process.exit(1);
    });

    child.on('close', (code) => {
        if (fs.existsSync(common.portFile)) fs.unlinkSync(common.portFile);
        process.exit(code);
    });
}

start().catch(err => {
    console.error("Failed to start launcher:", err);
    process.exit(1);
});