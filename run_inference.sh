./mvnw package -DskipTests dependency:copy-dependencies
java -Xmx4g -cp target/dependency/*:target/thesis-1.0-SNAPSHOT.jar nl.tudelft.cornul11.thesis.corpus.MainApp -m DETECTION_MODE -f "$1"