FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

# HTTP 8083, gRPC 9192
EXPOSE 8083 9192

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
