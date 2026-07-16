const fs = require('fs');
const path = require('path');
const https = require('https');
const crypto = require('crypto');

const pluginJsonPath = path.join(__dirname, '.claude-plugin', 'plugin.json');
const pluginData = JSON.parse(fs.readFileSync(pluginJsonPath, 'utf8'));
const version = pluginData.version;

const JAR_NAME = 'spring-boot-language-server-standalone-exec.jar';
const jarDir = path.join(__dirname, 'language-server');
const jarPath = path.join(jarDir, JAR_NAME);

const ALLOWED_HOST = 'cdn.spring.io';
const MAX_REDIRECTS = 5;

if (!fs.existsSync(jarDir)) {
    fs.mkdirSync(jarDir, { recursive: true });
}

function get(url, redirectCount = 0) {
    return new Promise((resolve, reject) => {
        let parsed;
        try {
            parsed = new URL(url);
        } catch (err) {
            reject(new Error(`Invalid URL: ${url}`));
            return;
        }
        if (parsed.protocol !== 'https:' || parsed.hostname !== ALLOWED_HOST) {
            reject(new Error(`Refusing to fetch from untrusted host: ${url}`));
            return;
        }
        https.get(url, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                res.resume();
                if (redirectCount >= MAX_REDIRECTS) {
                    reject(new Error(`Too many redirects while fetching ${url}`));
                    return;
                }
                resolve(get(new URL(res.headers.location, url).toString(), redirectCount + 1));
            } else if (res.statusCode === 200) {
                resolve(res);
            } else {
                reject(new Error(`Failed to fetch ${url}: ${res.statusCode}`));
            }
        }).on('error', reject);
    });
}

async function fetchText(url) {
    const res = await get(url);
    return new Promise((resolve, reject) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => resolve(data.trim()));
        res.on('error', reject);
    });
}

async function downloadFile(url, dest) {
    console.error(`Downloading: ${url}`);
    const res = await get(url);
    const hash = crypto.createHash('sha256');
    const fileStream = fs.createWriteStream(dest);
    return new Promise((resolve, reject) => {
        res.on('data', chunk => hash.update(chunk));
        res.on('error', reject);
        fileStream.on('error', reject);
        fileStream.on('finish', () => resolve(hash.digest('hex')));
        res.pipe(fileStream);
    });
}

async function install() {
    console.error(`Installing Spring Boot Language Server v${version}...`);

    const isSnapshot = version.includes('-');
    const baseUrl = isSnapshot
        ? `https://cdn.spring.io/spring-tools/snapshot/language-server/spring-boot/${JAR_NAME}`
        : `https://cdn.spring.io/spring-tools/release/language-server/spring-boot/${version}/${JAR_NAME}`;
    const sha256Url = `${baseUrl}.sha256`;

    try {
        const expectedSha256 = await fetchText(sha256Url);
        const actualSha256 = await downloadFile(baseUrl, jarPath);

        if (actualSha256.toLowerCase() !== expectedSha256.toLowerCase()) {
            fs.unlinkSync(jarPath);
            throw new Error(`Checksum mismatch for ${JAR_NAME}: expected ${expectedSha256}, got ${actualSha256}`);
        }

        console.error(`Successfully installed version ${version} to ${jarPath}`);
    } catch (err) {
        throw new Error(`Failed to download JAR from ${baseUrl}\nError: ${err.message}`);
    }
}

install().catch(err => {
    console.error("Installation failed:", err);
    process.exit(1);
});
