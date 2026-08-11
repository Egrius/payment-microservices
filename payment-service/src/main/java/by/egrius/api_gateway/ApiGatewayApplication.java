package by.egrius.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// TODO SSE for processed transfers     awaits
// TODO RabbitMQ integration			awaits
// TODO Caching 						done
// TODO idempotency
// TODO @Transaction failed test

@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
