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
    
    const isSnapshot = version.includes('-');
    const baseUrl = isSnapshot 
        ? `https://cdn.spring.io/spring-tools/snapshot/language-server/spring-boot/${JAR_NAME}`
        : `https://cdn.spring.io/spring-tools/release/language-server/spring-boot/${version}/${JAR_NAME}`;

    try {
        await downloadFile(baseUrl, jarPath);
        console.error(`Successfully installed version ${version} to ${jarPath}`);
    } catch (err) {
        throw new Error(`Failed to download JAR from ${baseUrl}\nError: ${err.message}`);
    }
}

install().catch(err => {
    console.error("Installation failed:", err);
    process.exit(1);
});