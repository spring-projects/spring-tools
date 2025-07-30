set -e

dist_path=$1
eclipse_version=$2
build_type=$3
s3_bucket=$4
download_url_root=$5

s3_url=s3://${s3_bucket}/${dist_path}
downloads_html="sts-nightly-${eclipse_version}.html"
files=`aws s3 cp ${s3_url} . --recursive --exclude "*" --include "spring-tools-for-eclipse*.zip" --include "spring-tools-for-eclipse*.dmg" --include "spring-tools-for-eclipse*.self-extracting.jar" --include "spring-tools-for-eclipse*.tar.gz" --exclude "*/*" --dryrun`
rm -f ./${downloads_html}
echo '<ul>' >> $downloads_html
s3_url_prefix="s3://${AWS_S3_BUCKET}"
s3_url_prefix_length=${#s3_url_prefix}
for file in $files
do
  if [[ "$file" =~ ^"${s3_url_prefix}" ]]; then
    download_url=${download_url_root}${file:$s3_url_prefix_length} #
    filename=${file:${#s3_url}+1}
    echo '  <li><a href="'${download_url}'">'${filename}'</li>' >> $downloads_html
  fi
done
echo '</ul>' >> $downloads_html
cat ./$downloads_html
aws s3 mv ./$downloads_html s3://${s3_bucket}/${build_type}/ --no-progress
