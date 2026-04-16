const fs = require('fs');
const path = require('path');
const https = require('https');

const pluginJsonPath = path.join(__dirname, '.claude-plugin', 'plugin.json');
const pluginData = JSON.parse(fs.readFileSync(pluginJsonPath, 'utf8'));
const version = pluginData.version;

const JAR_NAME = 'spring-boot-language-server-standalone-exec.jar';
const jarDir = path.join(__dirname, 'language-server');
const jarPath = path.join(jarDir, JAR_NAME);

if (!fs.existsSync(jarDir)) {
    fs.mkdirSync(jarDir, { recursive: true });
}

function get(url) {
    return new Promise((resolve, reject) => {
        https.get(url, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                resolve(get(res.headers.location));
            } else if (res.statusCode === 200) {
                resolve(res);
            } else {
                reject(new Error(`Failed to fetch ${url}: ${res.statusCode}`));
            }
        }).on('error', reject);
    });
}

async function downloadFile(url, dest) {
    console.error(`Downloading: ${url}`);
    const res = await get(url);
    const fileStream = fs.createWriteStream(dest);
    return new Promise((resolve, reject) => {
        res.pipe(fileStream);
        res.on('end', () => resolve());
        fileStream.on('error', reject);
    });
}

async function install() {
    console.error(`Installing Spring Boot Language Server v${version}...`);
    
    // First, try to download the stable release JAR for this version
    const releaseUrl = `https://cdn.spring.io/spring-tools/release/language-server/spring-boot/${version}/${JAR_NAME}`;
    const snapshotUrl = `https://cdn.spring.io/spring-tools/snapshot/language-server/spring-boot/${JAR_NAME}`;

    try {
        await downloadFile(releaseUrl, jarPath);
        console.error(`Successfully installed Release version to ${jarPath}`);
    } catch (releaseErr) {
        // If the release JAR is not found (e.g. 404 or 403 Access Denied), 
        // it means this version has not been officially released yet.
        // Fallback to downloading the bleeding-edge snapshot!
        console.error(`Release JAR not found for v${version}. Falling back to snapshot...`);
        try {
            await downloadFile(snapshotUrl, jarPath);
            console.error(`Successfully installed Snapshot version to ${jarPath}`);
        } catch (snapshotErr) {
            throw new Error(`Failed to download both Release and Snapshot JARs.\nRelease Error: ${releaseErr.message}\nSnapshot Error: ${snapshotErr.message}`);
        }
    }
}

install().catch(err => {
    console.error("Installation failed:", err);
    process.exit(1);
});