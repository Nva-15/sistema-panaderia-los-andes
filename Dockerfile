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
# Ajustes para que Java quepa en 512 MB (plan gratuito de Render):
#  - heap limitado al 50% de la memoria del contenedor (el resto lo usan metaspace, codigo e hilos)
#  - SerialGC: el recolector de basura que menos memoria consume
#  - pila de hilos mas pequena y compilador JIT simplificado (arranque mas rapido con poca CPU)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
