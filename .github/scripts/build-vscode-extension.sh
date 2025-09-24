set -e

extension_id=$1
dist_type=$2

workdir=`pwd`
sources=$workdir/vscode-extensions/$extension_id
# server_id=${extension_id#vscode-}

cd "$sources"

base_version=`jq -r .version package.json`
if [ "$dist_type" = release ]; then
    echo -e "\n\n*Version: ${base_version}-RELEASE*" >> README.md
    profiles=-Pgenerate-and-include-license-files
else
    if [ "$dist_type" = pre ]; then
        # for pre-release build, work the timestamp into package.json patch version. No minutes in the timestamp due VSCode pre-release lack of support for semver
        timestamp=`date -u +%Y%m%d%H`
        qualified_version=`echo $base_version | sed "s/\([0-9]\{1,\}.[0-9]\{1,\}.\)[0-9]\{1,\}/\1$timestamp/"`
        npm version ${qualified_version}
        echo -e "\n\n*Version: ${base_version}-PRE-RELEASE*" >> README.md
        profiles=-Pgenerate-and-include-license-files
    else
        # for snapshot build, work the timestamp into package.json version qualifier
        timestamp=`date -u +%Y%m%d%H%M`
        qualified_version=${base_version}-${timestamp}
        npm version ${qualified_version}
        echo -e "\n\n*Version: ${qualified_version}*" >> README.md
    fi
fi

if [ -f "./scripts/preinstall.sh" ]; then
   ./scripts/preinstall.sh $profiles
fi

npm install
if [ "$dist_type" = pre ]; then
    npm run vsce-pre-release-package
else
    npm run vsce-package
fi

# for release build we don't add version-qualifier to package.json
# So we must instead rename the file ourself to add a qualifier
#if [ "$dist_type" == release ]; then
#    vsix_file=`ls *.vsix`
#    git fetch --tags
#    release_name=`git tag --points-at HEAD | grep ${extension_id}`
#    echo "release_name=$release_name"
#    if [ -z "$release_name" ]; then
#        echo "Release Candidates must be tagged" >&2
#        exit 1
#    else
#        mv $vsix_file ${release_name}.vsix
#    fi
#fi

mkdir -p $workdir/vsix
cp *.vsix $workdir/vsix
#server_jar=$workdir/sts4/headless-services/${server_id}-language-server/target/*-exec.jar
#if [ -f $server_jar ]; then
#    cp $server_jar $workdir/out/${server_id}-language-server-${base_version}-${timestamp}.jar
#fi
