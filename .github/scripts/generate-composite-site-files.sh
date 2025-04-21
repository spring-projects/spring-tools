val=$1
url=$2
eversion=$3

rm -f ./compositeArtifacts.xml
rm -f ./compositeContent.xml
rm -f ./p2.index

echo "<?xml version='1.0' encoding='UTF-8'?>" >> compositeArtifacts.xml
echo "<?compositeArtifactRepository version='1.0.0'?>" >> compositeArtifacts.xml
echo "<repository name='Spring Tools for Eclipse'" >> compositeArtifacts.xml
echo "    type='org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository' version='1.0.0'>" >> compositeArtifacts.xml
echo "  <properties size='1'>" >> compositeArtifacts.xml
echo "    <property name='p2.timestamp' value='${val}'/>" >> compositeArtifacts.xml
echo "  </properties>" >> compositeArtifacts.xml
if [[ ${eversion} == "e4.36" ]]; then
  echo "  <children size='2'>" >> compositeArtifacts.xml
  echo "    <child location='${url}'/>" >> compositeArtifacts.xml
  echo "    <child location='https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/2025-06/'/>" >> compositeArtifacts.xml
  echo "  </children>" >> compositeArtifacts.xml
else
  echo "  <children size='1'>" >> compositeArtifacts.xml
  echo "    <child location='${url}'/>" >> compositeArtifacts.xml
  echo "  </children>" >> compositeArtifacts.xml
fi
echo "</repository>" >> compositeArtifacts.xml

echo "<?xml version='1.0' encoding='UTF-8'?>" >> compositeContent.xml
echo "<?compositeMetadataRepository version='1.0.0'?>" >> compositeContent.xml
echo "<repository name='Spring Tools for Eclipse'" >> compositeContent.xml
echo "    type='org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository' version='1.0.0'>" >> compositeContent.xml
echo "  <properties size='1'>" >> compositeContent.xml
echo "    <property name='p2.timestamp' value='${val}'/>" >> compositeContent.xml
echo "  </properties>" >> compositeContent.xml
if [[ ${eversion} == "e4.36" ]]; then
  echo "  <children size='2'>" >> compositeContent.xml
  echo "    <child location='${url}'/>" >> compositeContent.xml
  echo "    <child location='https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/2025-06/'/>" >> compositeContent.xml
  echo "  </children>" >> compositeContent.xml
else
  echo "  <children size='1'>" >> compositeContent.xml
  echo "    <child location='${url}'/>" >> compositeContent.xml
  echo "  </children>" >> compositeContent.xml
fi
echo "</repository>" >> compositeContent.xml

echo "version=1" >> p2.index
echo "metadata.repository.factory.order=compositeContent.xml,!" >> p2.index
echo "artifact.repository.factory.order=compositeArtifacts.xml,!" >> p2.index
