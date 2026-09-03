package com.example.auth;

import java.io.IOException;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentiseringen: sessionscookie plus CSRF-skydd.
 *
 * Valet av sessionscookie framför JWT i {@code localStorage} är medvetet.
 * Cookien är {@code HttpOnly} och därmed oåtkomlig för JavaScript - alltså
 * också för en eventuell XSS - och Spring äger hela livscykeln inklusive
 * utgång och invalidering. En JWT hade krävt egen lagring, egen utgång och
 * egen förnyelse utan att ge något tillbaka i en app som ändå har
 * server-side state.
 *
 * Detta är kodbasens enda {@code @Configuration}-klass; se javadocen i
 * {@link com.example.PsykologenApplication} för varför säkerheten är ett
 * medvetet undantag från appens composition root.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Delegerande encoder: förstår {bcrypt}-prefixet i konfigurationen och
        // gör det möjligt att byta algoritm senare utan att gamla hashar slutar fungera.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Kontona från konfigurationen. Valideringen sker här, vid uppstart, så
     * att felaktig konfiguration ger ett läsbart startfel via
     * {@link InvalidUsersFailureAnalyzer} i stället för att upptäckas först
     * när någon försöker logga in.
     *
     * Undantaget är {@code --hash-password}: då startas appen enbart för att
     * skriva ut en lösenordshash, och att kräva ett konfigurerat konto redan
     * där hade gjort det omöjligt att skapa sitt första konto.
     */
    @Bean
    public UserDetailsService userDetailsService(AuthProperties properties, ApplicationArguments arguments) {
        if (arguments.containsOption(PasswordHashRunner.OPTION)) {
            return new InMemoryUserDetailsManager();
        }

        properties.validate();

        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        for (AuthProperties.User configured : properties.getUsers()) {
            UserDetails user = User.withUsername(configured.getUsername())
                    .password(configured.getPasswordHash())
                    .roles("USER")
                    .build();
            manager.createUser(user);
        }
        return manager;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                        .anyRequest().authenticated())
                // Skyddet mot session fixation ligger i AuthController, inte här:
                // sessionFixation() gäller Spring Securitys egna inloggningsfilter,
                // och de är avstängda nedan till förmån för AuthController.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // Detta är ett API, inte en server-renderad app: en oinloggad
                // begäran ska ge 401 så frontend kan visa inloggningen, inte en
                // redirect till en inloggningssida som inte finns här.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }

    /**
     * Ser till att {@code XSRF-TOKEN}-cookien faktiskt skickas ut.
     *
     * Spring Security 6 skjuter upp genereringen av CSRF-token tills någon
     * läser den, och {@link CookieCsrfTokenRepository} sätter cookien först i
     * samma stund. Utan det här filtret får frontend därför aldrig någon
     * cookie att skicka tillbaka, och varje POST/PUT skulle svara 403.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
