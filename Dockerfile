FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 invoiceflow
USER invoiceflow
WORKDIR /app
COPY --from=build /workspace/target/invoiceflow-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
