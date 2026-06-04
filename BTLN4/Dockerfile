 
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

 
COPY pom.xml .
COPY src ./src

 
 
RUN mvn clean package -DskipTests

 
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

 
 
COPY --from=build /app/target/app-1.0-SNAPSHOT.jar ./app.jar

 
EXPOSE 7000

 
 
CMD ["java", "-Duser.timezone=Asia/Ho_Chi_Minh", "-Dauction.server.url=ws://0.0.0.0:7000/auction", "-cp", "/app/app.jar", "com.auction.ServerMain"]