package co.edu.unbosque.foodik.config;

import co.edu.unbosque.foodik.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/**", "/swagger-ui/**", "/swagger-ui.html",
            "/api-docs/**", "/actuator/health"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/restaurants/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/restaurants/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/scraping/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/restaurants/**").hasRole("RESTAURANT_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/restaurants/**").hasRole("RESTAURANT_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/restaurants/**").hasRole("RESTAURANT_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/menu-items/**").hasRole("RESTAURANT_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/menu-items/**").hasRole("RESTAURANT_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/menu-items/**").hasRole("RESTAURANT_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/discounts/**").hasRole("RESTAURANT_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/discounts/**").hasRole("RESTAURANT_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/discounts/**").hasRole("RESTAURANT_ADMIN")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}