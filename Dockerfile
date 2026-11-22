FROM maven:3.9.9-eclipse-temurin-17 AS build
ARG SERVICE=invoiceflow-api
WORKDIR /workspace
COPY pom.xml .
COPY invoiceflow-api/pom.xml invoiceflow-api/pom.xml
COPY invoiceflow-notifications/pom.xml invoiceflow-notifications/pom.xml
COPY invoiceflow-api/src invoiceflow-api/src
COPY invoiceflow-notifications/src invoiceflow-notifications/src
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode -pl "$SERVICE" -am -DskipTests package \
    && cp "/workspace/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar" /workspace/app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 invoiceflow
USER invoiceflow
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
