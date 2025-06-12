./mvnw clean package -Pe436 -Psnapshot -Dhttpclient.retry-max=20 -Dmaven.test.skip=true -Declipse.p2.mirrors=false -Dtycho.localArtifacts=ignore -Dorg.eclipse.equinox.p2.transport.ecf.retry=5
