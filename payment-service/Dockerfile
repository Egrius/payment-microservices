FROM amazoncorretto:21-alpine3.24-jdk

RUN apk update && apk upgrade && \
    addgroup -S appgroup && adduser -S appuser -G appgroup -u 1001

WORKDIR /app

COPY --chown=appuser:appgroup ./build/libs/api-gateway-0.0.1-SNAPSHOT.jar ./payment_service.jar

USER 1001

ENV DB_HOST=payment_db \
    DB_PORT=5432 \
    DB_NAME=api_gateway_db \
    DB_USER=postgres \
    DB_PASSWORD=2Pg8_06Egr \
    RABBIT_HOST=localhost \
    RABBIT_PORT=5672 \
    RABBIT_USER=guest \
    RABBIT_PASSWORD=guest \
    PAYMENT_SERVICE_PORT=8080

EXPOSE 8080

CMD ["java", "-jar", "payment_service.jar"]