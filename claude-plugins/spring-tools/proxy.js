const net = require('net');
const fs = require('fs');
const common = require('./common');

function waitForPort(retries = 60) {
    return new Promise((resolve, reject) => {
        const check = (attemptsLeft) => {
            if (fs.existsSync(common.portFile)) {
                const portStr = fs.readFileSync(common.portFile, 'utf8');
                const port = parseInt(portStr, 10);
                if (!isNaN(port)) {
                    resolve(port);
                    return;
                }
            }
            if (attemptsLeft > 0) {
                setTimeout(() => check(attemptsLeft - 1), 500);
            } else {
                reject(new Error("Timeout waiting for .lsp-port file to be created by launcher.js"));
            }
        };
        check(retries);
    });
}

async function start() {
    try {
        const port = await waitForPort();
        
        function connect(retries = 30) {
            const client = net.createConnection({ port: port, host: '127.0.0.1' }, () => {
                process.stdin.pipe(client);
                client.pipe(process.stdout);
            });

            client.on('error', (err) => {
                if (retries > 0) {
                    setTimeout(() => connect(retries - 1), 1000);
                } else {
                    console.error(`Failed to connect to LSP socket on port ${port}:`, err);
                    process.exit(1);
                }
            });

            client.on('close', () => {
                process.exit(0);
            });
        }
        
        connect();
    } catch (err) {
        console.error(err.message);
        process.exit(1);
    }
}

start();