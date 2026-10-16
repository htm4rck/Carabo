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
RUN mkdir -p /app/migrations \
    && cp /migrations/001-document-schema.sql /app/migrations/V1__document_schema.sql \
    && cp /migrations/003-gre-and-apikey.sql /app/migrations/V2__gre_and_api_key.sql \
    && cp /migrations/004-correlatives-and-idempotency.sql /app/migrations/V3__correlatives_and_idempotency.sql \
    && cp /migrations/005-api-key-hardening.sql /app/migrations/V4__api_key_hardening.sql \
    && cp /migrations/006-signing-and-note-metadata.sql /app/migrations/V5__signing_and_note_metadata.sql \
    && cp /migrations/007-utc-timestamps.sql /app/migrations/V6__utc_timestamps.sql \
    && test -s /app/migrations/V1__document_schema.sql \
    && test -s /app/migrations/V2__gre_and_api_key.sql \
    && test -s /app/migrations/V3__correlatives_and_idempotency.sql \
    && test -s /app/migrations/V4__api_key_hardening.sql \
    && test -s /app/migrations/V5__signing_and_note_metadata.sql \
    && test -s /app/migrations/V6__utc_timestamps.sql
RUN ./gradlew :carabo-starter-quarkus:quarkusBuild --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG CARABO_API_VERSION=0.2.0
ARG CARABO_BUILD_DATE=unknown
ARG CARABO_COMMIT=unknown
COPY --from=build /app/carabo-starter-quarkus/build/quarkus-app/ ./
COPY --from=build /app/migrations/ /app/migrations/
ENV FLYWAY_LOCATIONS=filesystem:/app/migrations
ENV CARABO_VERSION=$CARABO_API_VERSION \
    CARABO_BUILD_DATE=$CARABO_BUILD_DATE \
    CARABO_COMMIT=$CARABO_COMMIT
EXPOSE 8998
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
