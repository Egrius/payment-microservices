FROM gradle:9.5.1-jdk21-alpine AS build

WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle ./
COPY build.gradle ./

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon


FROM amazoncorretto:21-alpine3.24-jdk

RUN apk update && apk upgrade && \
    addgroup -S appgroup && adduser -S appuser -G appgroup -u 1001
RUN apk add --no-cache curl

WORKDIR /app

COPY --from=build --chown=appuser:appgroup /app/build/libs/notification_service.jar ./notification_service.jar

USER 1001

ENV  NOTIFICATION_SERVICE_PORT=8081 \
    AUTH_SERVER_PORT=9000


EXPOSE 8081

CMD ["java", "-jar", "notification_service.jar"]