FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

# pom.xml först: oförändrade beroenden ger ett cachat lager vid om-build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests


FROM eclipse-temurin:25-jre-alpine

# /data/users måste finnas och ägas av app redan här, annars skapar Docker
# volymens monteringspunkt som root och appen kan inte skriva där.
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app /data/users \
    && chown -R app:app /app /data/users

WORKDIR /app
COPY --from=build --chown=app:app /build/target/*.jar app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
