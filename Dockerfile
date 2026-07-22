# Fase 1: Compilación con Maven y Java 17
FROM maven:3.8.5-openjdk-17 AS build
COPY . /app
WORKDIR /app
RUN mvn -B -DskipTests clean install -Pdes

# Fase 2: Entorno de ejecución ligero con Java 17
FROM openjdk:17-jdk-slim
COPY --from=build /app/target/controlmedico-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dserver.port=${PORT:-8080}", "-jar", "app.jar"]
