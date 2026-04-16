const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');

const jarDir = path.join(__dirname, 'language-server');
const jarPath = path.join(jarDir, 'spring-boot-language-server-standalone-exec.jar');

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

// 2. Launch the Java process
const javaArgs = [
    "-Xmx1024m",
    "-Djdk.util.zip.disableZip64ExtraFieldValidation=true",
    "-Dspring.config.location=classpath:/application.properties",
    "-jar",
    jarPath,
    "--logging.level.root=OFF",
    "--spring.ai.mcp.server.stdio=true",
    "--languageserver.standalone=true",
    "--languageserver.standalone-port=5007"
];

const child = spawn('java', javaArgs, { stdio: 'inherit' });

child.on('error', (err) => {
    console.error('Failed to start Java process:', err);
    process.exit(1);
});

child.on('close', (code) => {
    process.exit(code);
});