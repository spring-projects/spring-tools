set -e

file=$1
sign_script=$2
self_extr_jar=$3
id=$4
filename="$(basename -- $file)"

workdir=`pwd`

echo "****************************************************************"
echo "*** Processing : ${file}"
echo "****************************************************************"
destination_folder_name=extracted_${filename}
echo "Extracting archive ${filename}"
unzip -q $file -d ./${destination_folder_name}
echo "Successfully extracted ${filename}"
sts_folder=`find ./${destination_folder_name} -maxdepth 1 -type d -name 'sts-*' -print -quit`
echo "Found STS distro folder: ${sts_folder}"
echo "About to sign win exe file: ${sts_folder}/SpringToolSuite4.exe (id = ${id})"
$sign_script ${sts_folder}/SpringToolSuite4.exe ${sts_folder}/SpringToolSuite4.exe "${id}-${filename%.*}" 30 900
echo "Adding to zip contents of a folder ${destination_folder_name}"
cd ${destination_folder_name}
zip -r -q ../$file .
cd ..
echo "Successfully zipped ${destination_folder_name} into ${file}"
#java -jar $self_extr_jar $file
echo "Remove temporary ${destination_folder_name}"
rm -rf ./${destination_folder_name}

echo "Creating checksums sha-256 and md5 for ${file}"
shasum -a 256 $file > ${file}.sha256
md5sum $file > ${file}.md5
#self_jar_file=${file%.*}.self-extracting.jar
#echo "Creating checksums sha-256 and md5 for ${self_jar_file}"
#shasum -a 256 $self_jar_file > ${self_jar_file}.sha256
#md5sum $self_jar_file > ${self_jar_file}.md5