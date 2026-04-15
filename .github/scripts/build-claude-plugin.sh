#!/bin/bash
set -e -x

plugin_id=$1
dist_type=$2

workdir=$(pwd)
sources=$workdir/claude-plugins/$plugin_id

cd "$sources"

base_version=$(jq -r .version .claude-plugin/plugin.json)

if [ "$dist_type" = release ]; then
    qualified_version=$base_version
else
    # for snapshot build, work the timestamp into version qualifier
    timestamp=$(date -u +%Y%m%d%H%M)
    qualified_version=${base_version}-${timestamp}
    
    # update plugin.json with snapshot version
    tmp=$(mktemp)
    jq ".version = \"${qualified_version}\"" .claude-plugin/plugin.json > "$tmp" && mv "$tmp" .claude-plugin/plugin.json
fi

echo "Building standalone LS jar..."
./build.sh

echo "Packaging Claude plugin..."
tar_name="${plugin_id}-${qualified_version}.tar.gz"

mkdir -p $workdir/claude-dist
tar -czf "$workdir/claude-dist/$tar_name" .claude-plugin .lsp.json .mcp.json proxy.js language-server README.md
