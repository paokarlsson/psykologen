FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /build

# Spola ligger som jar-filer i lib/, så det finns inga beroenden att hämta.
COPY lib ./lib
COPY Build.java .
COPY src ./src
RUN java Build.java compile \
    && rm lib/spola.test-*.jar lib/SHA256SUMS


FROM eclipse-temurin:25-jre-alpine

# /data/users måste finnas och ägas av app redan här, annars skapar Docker
# volymens monteringspunkt som root och appen kan inte skriva där.
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app /data/users \
    && chown -R app:app /app /data/users

WORKDIR /app
COPY --from=build --chown=app:app /build/lib ./lib
COPY --from=build --chown=app:app /build/out/main ./main

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "--module-path", "/app/lib:/app/main", "--module", "psykologen/se.olaslab.psykologen.PsykologenApplication"]
