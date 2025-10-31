# Takes a .tar.gz file as a parameter, entitlements.plist file path and icns file path
set -e

file=$1
entitlements=$2
icns=$3

filename="$(basename -- $file)"
dir="$(dirname "$file")"

echo "****************************************************************"
echo "*** Signing and creating DMG for: ${file}"
echo "****************************************************************"
destination_folder_name=extracted_${filename}
echo "Extracting archive ${filename} to ${dir}/${destination_folder_name}"
mkdir ${dir}/${destination_folder_name}
tar -zxf $file --directory ${dir}/${destination_folder_name}
echo "Successfully extracted ${filename}"

# sign problematic binaries
for f in `find ${dir}/${destination_folder_name}/SpringToolsForEclipse.app -type f | grep -E ".*/fsevents\.node$"`
do
  echo "Signing binary file: ${f}"
  codesign --verbose --deep --force --timestamp --entitlements "${entitlements}" --options=runtime --keychain "${KEYCHAIN}" -s "${MACOS_CERTIFICATE_ID}" $f
done

function signExecutableInsideJar() {
  local pwd=`pwd`
  for f in `find $1 -type f | grep -E $2`
  do
    echo "Looking for '$3' files inside ${f} to sign..."
    local f_name="$(basename -- $f)"
    local extracted_jar_dir=extracted_${f_name}
    rm -rf $extracted_jar_dir
    mkdir $extracted_jar_dir
    echo "Extracting archive ${f}"
    unzip -o -q $f -d ./${extracted_jar_dir}
    echo "Extracted successfully"
    for jnilib_file in `find $extracted_jar_dir -type f | grep -E "$4"`
    do
      echo "Signing binary file: ${jnilib_file}"
      codesign --verbose --deep --force --timestamp --entitlements "${entitlements}" --options=runtime --keychain "${KEYCHAIN}" -s "${MACOS_CERTIFICATE_ID}" $jnilib_file
    done
    cd $extracted_jar_dir
    zip -r -u ../$f .
    cd ..
    rm -rf $extracted_jar_dir

    echo "Signing JAR file: ${f}"
    codesign --verbose --deep --force --timestamp --entitlements "${entitlements}" --options=runtime --keychain "${KEYCHAIN}" -s "${MACOS_CERTIFICATE_ID}" $f
  done
  cd $pwd
}

function signExecutableInsideNestedJar() {
  local pwd=`pwd`
  for jar_file in `find $1 -type f | grep -E $2`
  do
    local f_name="$(basename -- $jar_file)"
    local extracted_jar_dir=extracted_${f_name}
    rm -rf $extracted_jar_dir
    mkdir $extracted_jar_dir
    echo "Extracting archive ${jar_file}"
    unzip -o -q $jar_file -d ./${extracted_jar_dir}
    signExecutableInsideJar $extracted_jar_dir $3 $4 $5
    cd $extracted_jar_dir
    zip -r -u ../$jar_file .
    cd ..
    rm -rf $extracted_jar_dir
    echo "Signing JAR file: ${jar_file}"
    codesign --verbose --deep --force --timestamp --entitlements "${entitlements}" --options=runtime --keychain "${KEYCHAIN}" -s "${MACOS_CERTIFICATE_ID}" $jar_file
  done
  cd $pwd
}

# sign libjansi.jnilib inside kotlin-compiler-embeddable.jar
signExecutableInsideJar ${dir}/${destination_folder_name}/SpringToolsForEclipse.app ".*/kotlin-compiler-embeddable.*\.jar$" "libjansi.jnilib" ".*/libjansi\.jnilib$"

# sign libsnappyjava.jnilib and libsnappyjava.dylib inside snappy-java.jar
signExecutableInsideJar ${dir}/${destination_folder_name}/SpringToolsForEclipse.app ".*/snappy-java.*\.jar$" "libsnappyjava.jnilib" ".*/libsnappyjava\.(jni|dy)lib$"

# sign libjnidispatch.jnilib inside jna.jar
signExecutableInsideJar ${dir}/${destination_folder_name}/SpringToolsForEclipse.app ".*/jna-\d+.*\.jar$" "libjnidispatch.jnilib.jnilib" ".*/libjnidispatch\.jnilib$"

#sign libjnidispatch.jnilib inside jna.jar which is inside org.springframework.ide.eclipse.docker.client.jar bundle
signExecutableInsideNestedJar ${dir}/${destination_folder_name}/SpringToolsForEclipse.app ".*/org.springframework.ide.eclipse.docker.client.*\.jar$" ".*/jna-\d+.*\.jar$" "libjnidispatch.jnilib" ".*/libjnidispatch\.jnilib$"

#sign libjnidispatch.jnilib inside develocity-gradle-plugin.jar which is inside rewrite-gradle.jar bundle
#signExecutableInsideNestedJar ${dir}/${destination_folder_name}/SpringToolsForEclipse.app ".*/rewrite-gradle-\d+.*\.jar$" ".*/develocity-gradle-plugin.*\.jar$" "libjnidispatch.jnilib" ".*/libjnidispatch\.jnilib$"

# Sign the app
ls -la ${dir}/${destination_folder_name}/SpringToolsForEclipse.app/
codesign --verbose --deep --force --timestamp --entitlements "${entitlements}" --options=runtime --keychain "${KEYCHAIN}" -s "${MACOS_CERTIFICATE_ID}" ${dir}/${destination_folder_name}/SpringToolsForEclipse.app

cd ${dir}/${destination_folder_name}
echo "Generating dmg-config.json..."
echo '{' >> dmg-config.json
echo '  "title": "Spring Tools for Eclipse",' >> dmg-config.json
echo '  "icon": "'$icns'",' >> dmg-config.json
echo '  "contents": [' >> dmg-config.json
echo '    { "x": 192, "y": 100, "type": "file", "path": "./SpringToolsForEclipse.app" },' >> dmg-config.json
echo '    { "x": 448, "y": 100, "type": "link", "path": "/Applications" },' >> dmg-config.json
echo '    { "x": 1000, "y": 2000, "type": "file", "path": "'$icns'", "name": ".VolumeIcon.icns" }' >> dmg-config.json
echo '  ],' >> dmg-config.json
echo '  "format": "UDZO"' >> dmg-config.json
echo '}' >> dmg-config.json
cat ./dmg-config.json
dmg_filename=${filename%.*.*}.dmg
appdmg ./dmg-config.json ../${dmg_filename}

cd ..
rm -rf ./${destination_folder_name}
rm -f $filename

echo "Sign ${dmg_filename}"
codesign --verbose --deep --force --timestamp --keychain "${KEYCHAIN}" -s "${MACOS_CERTIFICATE_ID}" ./$dmg_filename