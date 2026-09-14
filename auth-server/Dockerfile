FROM amazoncorretto:21-alpine3.24-jdk

RUN apk update && apk upgrade && \
    addgroup -S appgroup && adduser -S appuser -G appgroup -u 1001

RUN apk add --no-cache curl

WORKDIR /app

COPY --chown=appuser:appgroup ./build/libs/auth-server-0.0.1-SNAPSHOT.jar ./auth_server.jar

USER 1001

ENV AUTH_SERVER_PORT=9000

EXPOSE 9000

CMD ["java", "-jar", "auth_server.jar"]