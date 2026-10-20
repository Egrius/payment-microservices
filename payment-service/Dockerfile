FROM gradle:9.5.1-jdk21-alpine AS build

WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle ./
COPY build.gradle ./

RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew dependencies --no-daemon

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew bootJar --no-daemon


FROM amazoncorretto:21-alpine3.24-jdk

RUN addgroup -S appgroup && adduser -S appuser -G appgroup -u 1001

WORKDIR /app

COPY --from=build --chown=appuser:appgroup /app/build/libs/api-gateway-0.0.1-SNAPSHOT.jar ./payment_service.jar

USER 1001

EXPOSE 8080

CMD ["java", "-jar", "payment_service.jar"]