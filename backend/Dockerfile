# 1. ETAPA DEV (Para desarrollo y recarga en caliente)
FROM eclipse-temurin:17-jdk-jammy AS dev
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

EXPOSE 8080
CMD ["./mvnw", "spring-boot:run"]

# 2. ETAPA BUILD (Para compilar a producción)
FROM dev AS build
COPY src ./src
RUN ./mvnw clean package -DskipTests

# 3. ETAPA PROD (Imagen final liviana JRE)
FROM eclipse-temurin:17-jre-jammy AS prod
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]