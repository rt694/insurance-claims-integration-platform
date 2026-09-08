FROM eclipse-temurin:17.0.20_8-jdk-jammy AS build

WORKDIR /workspace
COPY services/claims-service/.mvn .mvn
COPY services/claims-service/mvnw ./mvnw
COPY services/policy-service/pom.xml ./pom.xml
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY services/policy-service/src src
RUN ./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:17.0.20_8-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/target/policy-service-0.0.1-SNAPSHOT.jar app.jar

USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
