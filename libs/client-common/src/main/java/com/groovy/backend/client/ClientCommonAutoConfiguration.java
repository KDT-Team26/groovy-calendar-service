package com.groovy.backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * UserServiceClient는 com.groovy.backend.client 패키지에 있어 각 서비스의 기본 컴포넌트 스캔
 * 범위(com.groovy.backend.&lt;service&gt;)에 들어오지 않는다 — libs:web-common/libs:security-common과
 * 동일한 이유로 Spring Boot 자동 설정으로 명시적으로 빈을 등록한다. calendar-service처럼
 * UserServiceClient를 안 쓰는 서비스에도 빈은 만들어지지만(사용하지 않으면 그냥 무해하게
 * 남아있음), identity-service.url 등 기본값이 있어 설정을 안 해도 기동에는 문제없다.
 */
@AutoConfiguration
public class ClientCommonAutoConfiguration {

	@Bean
	public UserServiceClient userServiceClient(
		@Value("${identity-service.url:http://identity-service:8081}") String identityServiceUrl,
		@Value("${identity-service.connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${identity-service.read-timeout-ms:3000}") long readTimeoutMs
	) {
		return new UserServiceClient(identityServiceUrl, connectTimeoutMs, readTimeoutMs);
	}
}
