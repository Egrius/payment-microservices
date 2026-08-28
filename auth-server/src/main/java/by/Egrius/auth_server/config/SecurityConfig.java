package by.Egrius.auth_server.config;

import by.Egrius.auth_server.entity.User;
import by.Egrius.auth_server.repository.UserRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Configuration
public class SecurityConfig {

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            System.out.println("=== LOGIN FAILED ===");
            System.out.println("Username: " + request.getParameter("username"));
            System.out.println("Exception: " + exception.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Login failed");
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authServerSecurityFilterChain(HttpSecurity http,
                                                             RegisteredClientRepository clientRepository,
                                                             OAuth2AuthorizationService authorizationService,
                                                             AuthorizationServerSettings authorizationServerSettings,
                                                             Environment environment) throws Exception {
        http
                .oauth2AuthorizationServer(authServer -> {
                    http.securityMatchers(c -> c
                            .requestMatchers(authServer.getEndpointsMatcher())
                            .requestMatchers("/login"));
                    authServer
                            .oidc(oidc -> {
                                oidc.userInfoEndpoint(userInfo -> userInfo.userInfoMapper(
                                        oidcUserInfoAuthenticationContext -> {
                                            Authentication authentication = oidcUserInfoAuthenticationContext.getAuthentication();
                                            String email = authentication.getName();

                                            User user = userRepository.findByEmail(email)
                                                    .orElseThrow(() -> new RuntimeException("User not found"));

                                            return OidcUserInfo.builder()
                                                    .claim("sub", email)
                                                    .claim("email", email)
                                                    .claim("username", user.getUsername())
                                                    .claim("public_id", user.getPublicId().toString())
                                                    .build();
                                        }
                                ));
                            })
                            .registeredClientRepository(clientRepository)
                            .authorizationService(authorizationService)
                            .authorizationServerSettings(authorizationServerSettings);
                })
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .defaultSuccessUrl("/")
                        .failureHandler(authenticationFailureHandler())
                        .permitAll()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                )
                .csrf(csrf -> {
                    if(Arrays.stream(environment.getActiveProfiles()).anyMatch("test"::equals)) {
                        csrf.ignoringRequestMatchers("/**");
                        System.out.println("✅ CSRF DISABLED FOR TEST PROFILE");
                    }
                });

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/register")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOriginPatterns(List.of("*"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    /*
        Client repository to get our api-gateway app from.
        Register api-gateway if it's not provided.
     */
    @Bean
    public RegisteredClientRepository clientRepository(JdbcTemplate jdbcTemplate) {
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcTemplate);

        if(repository.findByClientId("payment-service") == null) {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("payment-service")
                    .clientSecret("{noop}payment-service-password")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://payment-service.local:8080/login/oauth2/code/auth-server")
                    .postLogoutRedirectUri("http://payment-service.local:8080/login")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .tokenSettings(tokenSettings())
                    .build();

            repository.save(client);
        }
        return repository;
    }

    /*
        Authorization server. Default jdbc service provided.
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    /*
        Define end-points for oauth2
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://auth-server.local:9000")
                .build();
    }

    /*
        Here we get private and public keys for the application (aka Client) registered in the repository
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(KeyStore keyStore, KeyStoreProperties properties) throws KeyStoreException, JOSEException {
        RSAKey rsaKey = RSAKey.load(keyStore, properties.getAlias(), properties.getPassword().toCharArray());
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    public KeyStore keyStore(KeyStoreProperties properties) throws Exception {
        /* Getting the keys from keystore.jks file, that was created by keytool command
            keytool -genkeypair -alias auth-server -keyalg RSA -keysize 2048 -keystore keystore.jks -storepass changeit -keypass changeit -dname "CN=auth_server"
         */

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(
                new ClassPathResource("keystore/keystore.jks").getInputStream(),
                properties.getPassword().toCharArray()
        );
        return keyStore;
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> accessTokenCustomizer() {
        return context -> {
            System.out.println("=== JWT Token Customizer CALLED ===");

            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                System.out.println("Token type: ACCESS_TOKEN");

                Authentication authentication = context.getPrincipal();
                System.out.println("Authentication: " + authentication);
                System.out.println("Principal: " + authentication.getPrincipal());
                System.out.println("Principal class: " + authentication.getPrincipal().getClass().getName());

                Object principal = authentication.getPrincipal();

                if (principal instanceof org.springframework.security.core.userdetails.User) {

                    org.springframework.security.core.userdetails.User userDetails = (org.springframework.security.core.userdetails.User) principal;
                    String email = userDetails.getUsername();
                    System.out.println("Email: " + email);

                    User user = userRepository.findByEmail(email)
                            .orElseThrow(() -> new RuntimeException("User not found"));

                    context.getClaims()
                            .claim("username", user.getUsername())
                            .claim("email", email)
                            .claim("public_id", user.getPublicId().toString())
                            .claim("roles",  new ArrayList<>(user.getRoles())); // Updated to check for an admin

                } else {
                    System.out.println("Principal is NOT org.springframework.security.core.userdetails.User. It is: " + principal.getClass().getName());
                }
            } else {
                System.out.println("Token type is NOT ACCESS_TOKEN: " + context.getTokenType());
            }
        };
    }

    @Bean
    public TokenSettings tokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(1))
                .refreshTokenTimeToLive(Duration.ofDays(3))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}