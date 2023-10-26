FROM maven:3.8.6-openjdk-11-slim AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src src
COPY run_app.sh .
RUN mvn package -DskipTests -Drat.skip=true -Dcheckstyle.skip=true -Denforcer.skip=true dependency:copy-dependencies
RUN chmod +x run_app.sh

FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=build /app/target/maven-local-index-timestamp-lookup-7.0.3.jar .
COPY --from=build /app/target/dependency ./libs
COPY --from=build /app/run-app.sh .
RUN chmod +x run_app.sh

ENTRYPOINT ["/app/run-app.sh"]