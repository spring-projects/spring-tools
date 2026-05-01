const path = require('path');
const os = require('os');
const fs = require('fs');
const crypto = require('crypto');

// Since we can't get CLAUDE_SESSION_ID from args or env reliably across all contexts,
// we will hash the current working directory to create a unique port file per project.
// This ensures multiple Claude Code instances in different projects won't collide.
const projectDir = process.cwd();
const projectHash = crypto.createHash('md5').update(projectDir).digest('hex').substring(0, 8);

const portFile = path.join(os.tmpdir(), `spring-tools-lsp-port-${projectHash}`);

module.exports = {
    portFile,
};
