FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

# REST 8080, gRPC 9090 (siehe k6-Skripte)
EXPOSE 8080 9090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
