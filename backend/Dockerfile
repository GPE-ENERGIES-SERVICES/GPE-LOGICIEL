FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# Tests need Docker (Testcontainers): they run outside the image build.
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home app && mkdir -p /data/storage && chown app /data/storage
USER app
WORKDIR /app
COPY --from=build /app/target/backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
