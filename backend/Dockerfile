# ── Build stage: compile with Maven on JDK 17 ──
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/app/lib
COPY src ./src
RUN mvn -B clean compile -DskipTests

# ── Runtime stage: lean JRE 17, no Maven needed ──
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/classes ./classes
COPY --from=build /app/lib ./lib
ENTRYPOINT ["java", "-cp", "classes:lib/*", "bank.Main"]