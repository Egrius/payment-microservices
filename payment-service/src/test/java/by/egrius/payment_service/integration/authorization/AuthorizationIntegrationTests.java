package by.egrius.payment_service.integration.authorization;

import by.egrius.payment_service.dto.RegisterRequest;
import by.egrius.payment_service.dto.account.AccountCreateDto;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
    docker-compose --env-file src/test/resources/testcontainers/.env -f src/test/resources/testcontainers/docker-compose-test.yaml up
*/

/*
docker cp ./auth-server/src/main/resources/oauth2-registered-client-schema.sql testcontainers-auth_db-1:/tmp/
docker cp ./auth-server/src/main/resources/oauth2-authorization-schema.sql testcontainers-auth_db-1:/tmp/
docker cp ./auth-server/src/main/resources/oauth2-authorization-consent-schema.sql testcontainers-auth_db-1:/tmp/

docker exec -it testcontainers-auth_db-1 psql -U postgres -d api_gateway_auth_server_db -f /tmp/oauth2-registered-client-schema.sql
docker exec -it testcontainers-auth_db-1 psql -U postgres -d api_gateway_auth_server_db -f /tmp/oauth2-authorization-schema.sql
docker exec -it testcontainers-auth_db-1 psql -U postgres -d api_gateway_auth_server_db -f /tmp/oauth2-authorization-consent-schema.sql
 */

public class AuthorizationIntegrationTests {


    private static final int apiPort = 8080;
    private static final String BASE_URL = "http://payment-service:" + apiPort;
    private static final String AUTH_SERVER_URL = "http://auth-server:9000";

    @Test
    void shouldGetJwtTokenAndGetAccessToAccounts() throws Exception {

        CookieStore paymentCookieStore = new BasicCookieStore();
        HttpClient paymentClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
                .setDefaultCookieStore(paymentCookieStore)
                .build();

        BasicCookieStore cookieStore = new BasicCookieStore();
        HttpClient authClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
                .setDefaultCookieStore(cookieStore)
                .build();

        RestTemplate paymentRestTemplate = new RestTemplate(
                new HttpComponentsClientHttpRequestFactory(paymentClient)
        );

        RestTemplate authRestTemplate = new RestTemplate(
                new HttpComponentsClientHttpRequestFactory(authClient)
        );

        // Creating session for payment-service and getting redirection
        ResponseEntity<String> r1 = paymentRestTemplate.getForEntity(
                 new URI("http://payment-service:8080/oauth2/authorization/auth-server"),
                String.class
        );
        String location1 = r1.getHeaders().getFirst("Location");
        System.out.println("Location (1): " + location1);

        String sessionCookie = extractSessionCookie(paymentCookieStore);
        System.out.println("Session cookie: " + sessionCookie);


        // Creating session on auth-server by going to the redirection we got before
        ResponseEntity<String> r2 = authRestTemplate.getForEntity( new URI(location1), String.class);
        String location2 = r2.getHeaders().getFirst("Location");
        System.out.println("Location (2): " + location2);

        // Going to the location we got as a redirection in the previous response
        // It's a login endpoint with a login form
        ResponseEntity<String> r3 = authRestTemplate.getForEntity(new URI(location2), String.class);
        String csrf = extractCsrfToken(r3.getBody());
        System.out.println("CSRF: " + csrf);

        // Creating a form with filled data
        // This user must have been already created in auth-server database!
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "testEmail@gmail.com");
        form.add("password", "123Test");
        form.add("_csrf", csrf);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Creating a POST request to the login endpoint with the form we created
        ResponseEntity<String> r4 = authRestTemplate.postForEntity(
                location2,
                new HttpEntity<>(form, headers),
                String.class
        );

        // Getting the redirection after POST /login
        String location3 = r4.getHeaders().getFirst("Location");
        System.out.println("Location (3): " + location3);

        // We are supposed to get a CODE after successful login, if we get a redirection to the "/",
        // we have to repeat the request of location1 to get the CODE after auth-server already knows that we are logged in
        if (location3 != null && !location3.contains("code=")) {
            System.out.println("CODE was not obtained, repeating GET for the initial authorize URL...");
            ResponseEntity<String> r5 = authRestTemplate.getForEntity(
                    new URI(location1),
                    String.class
            );
            location3 = r5.getHeaders().getFirst("Location");
            System.out.println("Location (4): " + location3);
        }

        String code = extractCode(location3);
        System.out.println("CODE: " + code);
        assertThat(code).isNotNull();

        String callbackUrl = location3;

        // Going to the url with the CODE to exchange it on payment-service
        ResponseEntity<String> callbackResponse = paymentRestTemplate.getForEntity(
                new URI(callbackUrl),
                String.class
        );

        // Getting final location after code was exchanged
        String finalLocation = callbackResponse.getHeaders().getFirst("Location");
        System.out.println("Final Location: " + finalLocation);

        // Now we can get the access token from payment-service since it knows who we are
        ResponseEntity<Map> tokenResponse = paymentRestTemplate.getForEntity(
                "http://payment-service:8080/token",
                Map.class
        );

        Map<String, Object> tokenBody = tokenResponse.getBody();
        String accessToken = (String) tokenBody.get("access_token");

        System.out.println("Access Token: " + accessToken);
        assertThat(accessToken).isNotNull();

        // Going back to the main page
        ResponseEntity<String> mainPage = paymentRestTemplate.getForEntity(
                "http://payment-service:8080/",
                String.class
        );
        System.out.println("Main page: " + mainPage.getBody());
    }

    private String extractCode(String location) {
        if (location == null) return null;

        String[] parts = location.split("code=");
        if (parts.length > 1) {
            String codePart = parts[1];

            int ampIndex = codePart.indexOf('&');
            if (ampIndex > 0) {
                return codePart.substring(0, ampIndex);
            }
            return codePart;
        }
        return null;
    }

    @Test
    void shouldReturn401_WhenNoTokenProvided() {

        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.getForEntity(BASE_URL + "/api/accounts", String.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
            System.out.println("Got 401 as expected");
        }
    }

    @Test
    void shouldReturn401_WhenTokenIsInvalid() {
        String invalidToken = "some_random_string_that_is_not_a_jwt";


        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(invalidToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        assertThrows(HttpClientErrorException.Unauthorized.class, () -> {
            restTemplate.exchange(
                    BASE_URL + "/api/accounts",
                    HttpMethod.GET,
                    request,
                    String.class
            );
        });
    }

    @Test
    void shouldReturn404_WhenAccessingOtherUserResource() throws Exception {
        RegisterRequest registrationUserA = new RegisterRequest("User_A", "1234_user_A", "TestUser_A@gmail.com");
        RegisterRequest registrationUserB = new RegisterRequest("User_B", "1234_user_B", "TestUser_B@gmail.com");

        // 1. Явно загружаем драйвер
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL Driver not found", e);
        }

        String jdbcUrlAuthServer = "jdbc:postgresql://localhost:5433/api_gateway_auth_server_db";
        String jdbcUrlPaymentService = "jdbc:postgresql://localhost:5435/api_gateway_db";
        String user = "postgres";
        String password = "2Pg8_06Egr";

        clearUsersFromAuthServerDB(jdbcUrlAuthServer,user, password);
        clearAccountsFromPaymentServiceDB(jdbcUrlPaymentService, user, password);

        // 3. Регистрируем пользователей через API
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> userARegistrationResponse = restTemplate.postForEntity(
                BASE_URL + "/register",
                registrationUserA,
                String.class
        );
        ResponseEntity<String> userBRegistrationResponse = restTemplate.postForEntity(
                BASE_URL + "/register",
                registrationUserB,
                String.class
        );

        assertThat(userARegistrationResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(userBRegistrationResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 4. Читаем пользователей из БД
        try (Connection connection = DriverManager.getConnection(jdbcUrlAuthServer, user, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT public_id, username, email FROM users"))
        {

            List<Map<String, String>> users = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, String> userMap = new HashMap<>();
                userMap.put("public_id", resultSet.getString("public_id"));
                userMap.put("username", resultSet.getString("username"));
                userMap.put("email", resultSet.getString("email"));
                users.add(userMap);
            }

            System.out.println("=== Users in DB ===");
            for (Map<String, String> u : users) {
                System.out.println(u);
            }

            assertThat(users).hasSize(2);
        }

        String tokenA = getAccessTokenForUser(registrationUserA.email(), "1234_user_A");
        System.out.println("Token A: " + tokenA);

        String tokenB = getAccessTokenForUser(registrationUserB.email(), "1234_user_B");
        System.out.println("Token B: " + tokenB);

        String accountAId = createAccount(tokenA, "Account_A", "USD");
        String accountBId = createAccount(tokenB, "Account_B", "USD");
        System.out.println("Account A: " + accountAId);
        System.out.println("Account B: " + accountBId);

        clearUsersFromAuthServerDB(jdbcUrlAuthServer,user, password);
        clearAccountsFromPaymentServiceDB(jdbcUrlPaymentService, user, password);

        // Getting User_B's Account from User_A

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        HttpClientErrorException.NotFound exception =
                assertThrows(HttpClientErrorException.NotFound.class, () -> {
                    restTemplate.exchange(
                            BASE_URL + "/api/accounts/" + accountBId,
                            HttpMethod.GET,
                            request,
                            String.class
                    );
                });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

    }

    private String getAccessTokenForUser(String email, String password) throws Exception {

        CookieStore paymentCookieStore = new BasicCookieStore();
        HttpClient paymentClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
                .setDefaultCookieStore(paymentCookieStore)
                .build();

        BasicCookieStore cookieStore = new BasicCookieStore();
        HttpClient authClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
                .setDefaultCookieStore(cookieStore)
                .build();

        RestTemplate paymentRestTemplate = new RestTemplate(
                new HttpComponentsClientHttpRequestFactory(paymentClient)
        );
        RestTemplate authRestTemplate = new RestTemplate(
                new HttpComponentsClientHttpRequestFactory(authClient)
        );

        // 1. GET /oauth2/authorization/auth-server
        ResponseEntity<String> r1 = paymentRestTemplate.getForEntity(
                "http://payment-service:8080/oauth2/authorization/auth-server",
                String.class
        );
        String location1 = r1.getHeaders().getFirst("Location");

        // 2. GET /oauth2/authorize
        ResponseEntity<String> r2 = authRestTemplate.getForEntity(new URI(location1), String.class);
        String location2 = r2.getHeaders().getFirst("Location");

        // 3. GET /login
        ResponseEntity<String> r3 = authRestTemplate.getForEntity(new URI(location2), String.class);
        String csrf = extractCsrfToken(r3.getBody());

        // 4. POST /login (с переданными username/password)
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", email);
        form.add("password", password);
        form.add("_csrf", csrf);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> r4 = authRestTemplate.postForEntity(
                new URI(location2),
                new HttpEntity<>(form, headers),
                String.class
        );

        String location3 = r4.getHeaders().getFirst("Location");
        if (location3 != null && !location3.contains("code=")) {
            ResponseEntity<String> r5 = authRestTemplate.getForEntity(new URI(location1), String.class);
            location3 = r5.getHeaders().getFirst("Location");
        }

        String code = extractCode(location3);

        ResponseEntity<String> callbackResponse = paymentRestTemplate.getForEntity(new URI(location3), String.class);

        ResponseEntity<Map> tokenResponse = paymentRestTemplate.getForEntity(
                "http://payment-service:8080/token",
                Map.class
        );

        return (String) tokenResponse.getBody().get("access_token");
    }

    private String createAccount(String token, String name, String currency) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        AccountCreateDto dto = new AccountCreateDto(name, currency);
        HttpEntity<AccountCreateDto> request = new HttpEntity<>(dto, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/api/accounts/create",
                HttpMethod.POST,
                request,
                Map.class
        );

        return (String) response.getBody().get("publicId");
    }

    private void clearUsersFromAuthServerDB(String jdbcUrl, String user, String password) throws SQLException {

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             Statement statement = connection.createStatement()) {
            int deleted = statement.executeUpdate("DELETE FROM users");
            System.out.println("✅ Deleted " + deleted + " users");
        } catch (SQLException e) {
            System.err.println("❌ Error deleting users: " + e.getMessage());
            throw e;
        }
    }

    private void clearAccountsFromPaymentServiceDB(String jdbcUrl, String user, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
             Statement statement = connection.createStatement()) {
            int deleted = statement.executeUpdate("DELETE FROM accounts");
            System.out.println("✅ Deleted " + deleted + " accounts");
        } catch (SQLException e) {
            System.err.println("❌ Error deleting accounts: " + e.getMessage());
            throw e;
        }
    }

    private String extractSessionCookie(CookieStore cookieStore) {
        return cookieStore.getCookies().stream()
                .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .findFirst()
                .orElse(null);
    }

    private String extractCsrfToken(String html) {
        if (html == null) return null;
        Document doc = Jsoup.parse(html);
        Element csrfInput = doc.select("input[name='_csrf']").first();
        if (csrfInput != null) {
            return csrfInput.val();
        }
        return null;
    }
}
