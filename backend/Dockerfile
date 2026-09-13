# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Cache dependencies in their own layer before copying source, so a source-only change doesn't
# force Maven to re-download the whole repository on every image build.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src
RUN ./mvnw -q clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S seatlock && adduser -S seatlock -G seatlock
COPY --from=build /app/target/seatlock-*.jar app.jar
USER seatlock

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
