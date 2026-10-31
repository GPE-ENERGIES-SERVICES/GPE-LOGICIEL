FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# Tests need Docker (Testcontainers): they run outside the image build.
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
# LibreOffice converts the Word documents of the consultation files to PDF;
# Tesseract (Apache 2.0) reads the scanned pages, in French, on the server;
# libheif (LGPL) decodes the HEIC photos of the iPhones before they are read.
RUN apt-get update \
	&& apt-get install -y --no-install-recommends libreoffice-writer-nogui fonts-dejavu-core \
		tesseract-ocr tesseract-ocr-fra libheif-examples libheif-plugin-libde265 \
	&& rm -rf /var/lib/apt/lists/*
RUN useradd --system --create-home app && mkdir -p /data/storage && chown app /data/storage
USER app
WORKDIR /app
COPY --from=build /app/target/backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
