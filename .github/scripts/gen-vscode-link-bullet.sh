id=$1
label=$2
download_url_root=$3

vsix_file=`aws s3 ls s3://$AWS_S3_BUCKET/snapshot/sts4/vscode-extensions/$id/ | awk '{$1=$2=$3=""; print $0}' | awk '{$1=$1};1' | grep "\.vsix$"`
if [ ! -z "${vsix_file}" ]; then
  echo "<li>${label}: <a href=\"${download_url_root}/snapshot/sts4/vscode-extensions/${id}/${vsix_file}\">${vsix_file}</a></li>"
fi