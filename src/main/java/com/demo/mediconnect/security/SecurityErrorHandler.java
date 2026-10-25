package com.demo.mediconnect.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(SecurityErrorHandler.class);
	private static final Set<String> TOKEN_CODES = Set.of("AUTH_INVALID_TOKEN", "AUTH_INVALID_EXP",
			"AUTH_INVALID_IAT", "AUTH_INVALID_NBF", "AUTH_TOKEN_EXPIRED", "AUTH_TOKEN_NOT_ACTIVE");

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		String code = "AUTH_INVALID_TOKEN";
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof JwtException && TOKEN_CODES.contains(cause.getMessage())) {
				code = cause.getMessage();
				break;
			}
		}
		response.setHeader("WWW-Authenticate", "Bearer");
		write(response, 401, code);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException exception) throws IOException {
		String code = "AUTH_FORBIDDEN_INSUFFICIENT_ROLE".equals(exception.getMessage())
				? "AUTH_FORBIDDEN_INSUFFICIENT_ROLE" : "AUTH_FORBIDDEN_INSUFFICIENT_SCOPE";
		write(response, 403, code);
	}

	private void write(HttpServletResponse response, int status, String code) throws IOException {
		LOGGER.atWarn().addKeyValue("code", code).log("Request denied");
		response.setStatus(status);
		response.setContentType("application/json");
		response.setHeader("Cache-Control", "no-store");
		response.getWriter().write("{\"code\":\"" + code + "\"}");
	}
}