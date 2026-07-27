FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY contracts ./contracts
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean verify

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/reporting-service-2.0.0.jar app.jar
RUN apk add --no-cache curl
EXPOSE 8096
ENTRYPOINT ["java", "-jar", "app.jar"]
