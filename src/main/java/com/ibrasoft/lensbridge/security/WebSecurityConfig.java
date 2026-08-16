package com.ibrasoft.lensbridge.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ibrasoft.tcketmanage.autoconfigure.TcketManagePaths;
import org.springframework.http.HttpMethod;

import com.ibrasoft.lensbridge.security.jwt.AuthEntryPointJwt;
import com.ibrasoft.lensbridge.security.jwt.AuthTokenFilter;
import com.ibrasoft.lensbridge.security.services.UserDetailsServiceImpl;

import java.util.Arrays;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {
  @Autowired
  UserDetailsServiceImpl userDetailsService;

  @Autowired
  private AuthEntryPointJwt unauthorizedHandler;

  @Value("${frontend.baseurl}")
  private String frontendBaseUrl;

  @Value("${musallahboard.baseurl}")
  private String musallahBoardBaseUrl;

  @Value("${minbar.baseurl}")
  private String minbarBaseUrl;

  @Bean
  public AuthTokenFilter authenticationJwtTokenFilter() {
    return new AuthTokenFilter();
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());

    return authProvider;
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
    return authConfig.getAuthenticationManager();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(frontendBaseUrl, musallahBoardBaseUrl, minbarBaseUrl));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, TcketManagePaths tcket) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource())) 
        .csrf(csrf -> csrf.disable())
        .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll()
            .requestMatchers("/api/gallery/**").permitAll()
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/api/events/**").permitAll()
            .requestMatchers("/api/musallah/**").permitAll()
            .requestMatchers("/api/minbar/**").permitAll()
            .requestMatchers("/api/refresh-musallahboard").permitAll()
            .requestMatchers("/api/agent/enroll").permitAll()
            .requestMatchers("/api/agent/ws").permitAll()
            .requestMatchers("/api/dashboard/ws/**").permitAll()
            // Disable these endpoints in production; they are only for local dev and CI.
            .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**").permitAll()
            // tcketmanage-core publishes the patterns that must stay reachable without a login:
            // public event browsing, guest checkout, buyer self-service and payment webhooks.
            // These are not unprotected -- GET /orders/{id}, POST /orders/{id}/cancel and
            // GET /tickets/{id} enforce ownership per entity via OrderAccessPolicy, which a URL
            // rule cannot express. Everything else under the mount point needs authentication.
            .requestMatchers(HttpMethod.GET, tcket.publicGetPatterns()).permitAll()
            .requestMatchers(HttpMethod.POST, tcket.publicPostPatterns()).permitAll()
            .anyRequest().authenticated());

    http.authenticationProvider(authenticationProvider());

    http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
