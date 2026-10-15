FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY apps/api/gradle/ gradle/
COPY apps/api/gradlew apps/api/settings.gradle apps/api/build.gradle apps/api/gradle.properties ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN sed -i '/org.gradle.java.home/d' gradle.properties
COPY apps/api/carabo-bom/ carabo-bom/
COPY apps/api/carabo-core/ carabo-core/
COPY apps/api/carabo-signer/ carabo-signer/
COPY apps/api/carabo-ose-client/ carabo-ose-client/
COPY apps/api/carabo-sunat-client/ carabo-sunat-client/
COPY apps/api/carabo-storage/ carabo-storage/
COPY apps/api/carabo-scheduler/ carabo-scheduler/
COPY apps/api/carabo-starter-quarkus/ carabo-starter-quarkus/
COPY migrations/ /migrations/
RUN ./gradlew :carabo-starter-quarkus:quarkusBuild --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/carabo-starter-quarkus/build/quarkus-app/ ./
EXPOSE 8998
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
