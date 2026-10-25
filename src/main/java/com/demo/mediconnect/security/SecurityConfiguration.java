package com.demo.mediconnect.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	JwtDecoder jwtDecoder(@Value("${mediconnect.security.public-key}") String publicKey,
			@Value("${mediconnect.security.issuer}") String issuer,
			@Value("${mediconnect.security.audience}") String audience,
			@Value("${mediconnect.security.clock-skew-seconds:30}") long skewSeconds) throws Exception {
		RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
				.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
		return new PatientJwtDecoder(key, issuer, audience, Clock.systemUTC(), skewSeconds);
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder,
			SecurityErrorHandler errors) throws Exception {
		return http.csrf(csrf -> csrf.disable())
				.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/api/patients", "/api/patients/**").authenticated()
						.anyRequest().denyAll())
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors))
				.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder))
						.authenticationEntryPoint(errors).accessDeniedHandler(errors))
				.build();
	}
}