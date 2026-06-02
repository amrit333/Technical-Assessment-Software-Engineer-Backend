# Build stage
FROM maven:3.8.5-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies for layer caching
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV DB_HOST=db
ENV DB_PORT=5432
ENV DB_NAME=inventory_db
ENV DB_USER=postgres
ENV DB_PASS=postgres
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
