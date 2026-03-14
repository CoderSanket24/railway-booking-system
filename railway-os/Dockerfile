# STAGE 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# This compiles your Java code into a .jar file
RUN mvn clean package -DskipTests

# STAGE 2: Run the application
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
# Copy the built .jar file from Stage 1
COPY --from=build /app/target/*.jar app.jar
# Expose the port Spring Boot runs on
EXPOSE 8080
# The command to start your OS Schedulers
ENTRYPOINT ["java", "-jar", "app.jar"]