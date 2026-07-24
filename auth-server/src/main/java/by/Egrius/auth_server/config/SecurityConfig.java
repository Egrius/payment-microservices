package by.Egrius.auth_server.config;

import by.Egrius.auth_server.entity.CustomUserDetails;
import by.Egrius.auth_server.entity.User;
import by.Egrius.auth_server.repository.UserRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
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
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration
public class SecurityConfig {

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authServerSecurityFilterChain(HttpSecurity http,
                                                             RegisteredClientRepository clientRepository,
                                                             OAuth2AuthorizationService authorizationService,
                                                             AuthorizationServerSettings authorizationServerSettings) throws Exception {
        http
                .oauth2AuthorizationServer(authServer -> {
                    http.securityMatcher(authServer.getEndpointsMatcher());
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
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(form -> form.loginPage("/login"))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**", "/login", "/register")  // <-- ДОБАВИТЬ securityMatcher!
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/register", "/login", "/oauth2/**").permitAll()  // <-- ДОБАВИТЬ /login
                        .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults());

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

        if(repository.findByClientId("api-gateway") == null) {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("api-gateway")
                    .clientSecret("{noop}api-gateway-password")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://api-gateway.local:8080/custom/oauth2/callback")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
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
                    System.out.println("Principal is CustomUserDetails");
                    org.springframework.security.core.userdetails.User userDetails = (org.springframework.security.core.userdetails.User) principal;
                    String email = userDetails.getUsername();
                    System.out.println("Email: " + email);

                    User user = userRepository.findByEmail(email)
                            .orElseThrow(() -> new RuntimeException("User not found"));

                    context.getClaims()
                            .claim("username", user.getUsername())
                            .claim("email", email)
                            .claim("public_id", user.getPublicId().toString());

                } else {
                    System.out.println("Principal is NOT org.springframework.security.core.userdetails.User. It is: " + principal.getClass().getName());
                }
            } else {
                System.out.println("Token type is NOT ACCESS_TOKEN: " + context.getTokenType());
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}