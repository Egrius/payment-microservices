package by.egrius.payment_service.integration.authorization;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void shouldReturn401WhenNoAuthenticationForTheResource() {

        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.getForEntity(BASE_URL + "/api/accounts", String.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
            System.out.println("Got 401 as expected");
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
