# Psykologen Backend

Spring Boot-backend för Psykologen-applikationen.

## Förutsättningar

- Java 21
- Maven

## Starta backend

```bash
mvn spring-boot:run
```

Alternativt:
```bash
mvn clean install
java -jar target/psykologen-1.0-SNAPSHOT.jar
```

Backend startar på port 8080 (default).

## Bygga för produktion

```bash
mvn clean package
```

JAR-filen skapas i `target/` mappen.

## Teknologier

- Spring Boot 3.1.5
- Java 21
- Maven
- Gson för JSON-hantering