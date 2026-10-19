# Imagen del backend en dos etapas: se compila con Maven y se ejecuta con un JRE liviano, sin usuario root.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Primero solo lo que define las dependencias: mientras no cambie el pom, Docker reutiliza esta capa.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/clinica-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 clinica
COPY --from=build /app/app.jar app.jar
USER clinica
EXPOSE 8080
# El heap se calcula sobre la memoria que le dé el contenedor (los planes gratuitos de los hostings dan poca).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]
