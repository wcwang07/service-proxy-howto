FROM maven:3.9.9-eclipse-temurin-11 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:11-jre

WORKDIR /app
COPY --from=build /workspace/target/service-proxy-howto.jar /app/service-proxy-howto.jar
COPY src/main/resources/config.yaml /app/config.yaml

EXPOSE 8090

ENV NEO4J_URI=neo4j://neo4j:7687 \
    NEO4J_USER=neo4j \
    NEO4J_PASSWORD=password \
    NEO4J_DATABASE=neo4j

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/service-proxy-howto.jar"]
