FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY contracts ./contracts
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean verify

FROM eclipse-temurin:17-jre-alpine

# The runtime base currently ships OpenSSL 3.5.7-r0. Refresh only the runtime
# packages to Alpine's fixed CVE-2026-14456 build.
RUN apk add --no-cache --upgrade \
    libcrypto3=3.5.8-r0 \
    libssl3=3.5.8-r0 \
    openssl=3.5.8-r0
WORKDIR /app
RUN apk upgrade --no-cache \
    && apk add --no-cache curl \
    && addgroup -S application \
    && adduser -S -G application application
COPY --from=build --chown=application:application /app/target/reporting-service-2.0.0.jar app.jar
USER application
EXPOSE 8096
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl --fail --silent http://localhost:8096/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
