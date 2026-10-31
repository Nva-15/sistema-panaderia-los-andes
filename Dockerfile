# ---------- Etapa 1: compilar el proyecto con Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: imagen liviana solo con Java para ejecutar ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8081
EXPOSE 8081
# MaxRAMPercentage ajusta la memoria de Java al limite del contenedor (plan gratuito de Render: 512 MB)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
