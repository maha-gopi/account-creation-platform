# Stage 1: Build the application and its dependencies
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy the root pom.xml
COPY ./pom.xml .

# Copy the shared library module
COPY ./shared-library ./shared-library

# Copy the core account API module
COPY ./core-account-api ./core-account-api

# Build the core-account-api and its dependencies (shared-library)
RUN mvn clean package -pl core-account-api -am -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built jar from the core-account-api module
COPY --from=builder /app/core-account-api/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
