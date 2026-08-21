package com.groovy.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * JwksKeyLocator/TokenProvider는 com.groovy.backend.security 패키지에 있어 각 서비스의 기본
 * 컴포넌트 스캔 범위(com.groovy.backend.&lt;service&gt;)에 들어오지 않는다 — 그래서 스캔에 기대는
 * 대신, Spring Boot 자동 설정으로 이 모듈을 의존성에 추가한 서비스(study/calendar/content/
 * notification-service)에 명시적으로 빈을 등록한다. identity-service는 이 모듈을 의존하지
 * 않으므로(자기 로컬 TokenProvider를 씀) 이 자동 설정이 적용되지 않는다.
 */
@AutoConfiguration
public class SecurityCommonAutoConfiguration {

	@Bean
	public JwksKeyLocator jwksKeyLocator(
		@Value("${jwt.jwks-url}") String jwksUrl,
		@Value("${jwt.jwks-connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${jwt.jwks-read-timeout-ms:3000}") long readTimeoutMs,
		@Value("${jwt.jwks-cache-ttl-seconds:300}") long cacheTtlSeconds
	) {
		return new JwksKeyLocator(jwksUrl, connectTimeoutMs, readTimeoutMs, cacheTtlSeconds);
	}

	@Bean
	public TokenProvider tokenProvider(JwksKeyLocator jwksKeyLocator) {
		return new TokenProvider(jwksKeyLocator);
	}
}
