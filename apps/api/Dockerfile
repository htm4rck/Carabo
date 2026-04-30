FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle gradle.properties ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN sed -i '/org.gradle.java.home/d' gradle.properties
COPY carabo-bom/ carabo-bom/
COPY carabo-core/ carabo-core/
COPY carabo-signer/ carabo-signer/
COPY carabo-ose-client/ carabo-ose-client/
COPY carabo-sunat-client/ carabo-sunat-client/
COPY carabo-storage/ carabo-storage/
COPY carabo-scheduler/ carabo-scheduler/
COPY carabo-starter-quarkus/ carabo-starter-quarkus/
RUN ./gradlew :carabo-starter-quarkus:quarkusBuild --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/carabo-starter-quarkus/build/quarkus-app/ ./
EXPOSE 8998
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
