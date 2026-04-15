const net = require('net');

function connect(retries = 30) {
    const client = net.createConnection({ port: 5007, host: '127.0.0.1' }, () => {
        process.stdin.pipe(client);
        client.pipe(process.stdout);
    });

    client.on('error', (err) => {
        if (retries > 0) {
            setTimeout(() => connect(retries - 1), 1000);
        } else {
            console.error("Failed to connect to LSP socket:", err);
            process.exit(1);
        }
    });

    client.on('close', () => {
        process.exit(0);
    });
}

connect();