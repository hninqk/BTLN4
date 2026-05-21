# Use Maven to build the application
FROM maven:3.9.6-eclipse-temurin-25-alpine AS build
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build the application
# We skip tests to speed up the build in the CI/CD environment
RUN mvn clean package -DskipTests

# Run the application using a smaller JRE image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy the shaded JAR from the build stage
# The artifactId in pom.xml is 'app', so the JAR name will be 'app-1.0-SNAPSHOT.jar'
COPY --from=build /app/target/app-1.0-SNAPSHOT.jar ./app.jar

# Expose the port Javalin runs on
EXPOSE 7000

# Run the server
# We explicitly point to com.auction.ServerMain so it doesn't try to start the JavaFX UI
CMD ["java", "-Dauction.server.url=ws://0.0.0.0:7000/auction", "-cp", "/app/app.jar", "com.auction.ServerMain"]
