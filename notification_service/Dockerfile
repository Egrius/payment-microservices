FROM amazoncorretto:21-alpine3.24-jdk

RUN apk update && apk upgrade && \
    addgroup -S appgroup && adduser -S appuser -G appgroup -u 1001
RUN apk add --no-cache curl

WORKDIR /app

COPY --chown=appuser:appgroup ./build/libs/notification_service.jar ./notification_service.jar

USER 1001

ENV  NOTIFICATION_SERVICE_PORT=8081 \
    AUTH_SERVER_PORT=9000


EXPOSE 8081

CMD ["java", "-jar", "notification_service.jar"]