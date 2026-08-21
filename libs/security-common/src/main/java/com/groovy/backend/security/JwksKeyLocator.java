package com.groovy.backend.security;

import java.net.http.HttpClient;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;

import lombok.extern.slf4j.Slf4j;

/**
 * study/calendar/content/notification-service 4곳에 복붙되어 있던 클래스를 security-common으로
 * 통합했다(SecurityCommonAutoConfiguration이 빈으로 등록). identity-service의 JWKS 엔드포인트에서
 * 공개키를 가져와 캐싱하고, 실패 시 기존 캐시를 유지한다(Phase 11 Resilience 패턴).
 */
@Slf4j
public class JwksKeyLocator implements Locator<Key> {

	private final RestClient restClient;
	private final Duration cacheTtl;
	private final CircuitBreaker circuitBreaker;
	private final Retry retry;
	private final ReentrantLock lock = new ReentrantLock();

	private volatile Map<String, Key> keysByKid = Map.of();
	private volatile Instant lastFetchedAt = Instant.EPOCH;

	public JwksKeyLocator(
		@Value("${jwt.jwks-url}") String jwksUrl,
		@Value("${jwt.jwks-connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${jwt.jwks-read-timeout-ms:3000}") long readTimeoutMs,
		@Value("${jwt.jwks-cache-ttl-seconds:300}") long cacheTtlSeconds
	) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		this.restClient = RestClient.builder().baseUrl(jwksUrl).requestFactory(requestFactory).build();
		this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);

		this.circuitBreaker = CircuitBreaker.of("jwks", CircuitBreakerConfig.custom()
			.failureRateThreshold(50)
			.slidingWindowSize(5)
			.minimumNumberOfCalls(3)
			.waitDurationInOpenState(Duration.ofSeconds(10))
			.permittedNumberOfCallsInHalfOpenState(1)
			.build());
		this.circuitBreaker.getEventPublisher().onStateTransition(
			event -> log.warn("JWKS CircuitBreaker 상태 전이: {}", event.getStateTransition()));

		this.retry = Retry.of("jwks", RetryConfig.custom()
			.maxAttempts(3)
			.intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(200), 2.0))
			.build());
		this.retry.getEventPublisher().onRetry(
			event -> log.warn("JWKS 조회 재시도: attempt={}, lastError={}",
				event.getNumberOfRetryAttempts(), event.getLastThrowable().toString()));
	}

	@Override
	public Key locate(Header header) {
		if (!(header instanceof ProtectedHeader protectedHeader)) {
			return null;
		}
		String kid = protectedHeader.getKeyId();
		if (kid == null) {
			return null;
		}

		if (isStale()) {
			refresh();
		}
		Key key = keysByKid.get(kid);
		if (key == null) {
			refresh();
			key = keysByKid.get(kid);
		}
		return key;
	}

	private boolean isStale() {
		return Instant.now().isAfter(lastFetchedAt.plus(cacheTtl));
	}

	private void refresh() {
		lock.lock();
		try {
			Supplier<String> fetch = () -> restClient.get().retrieve().body(String.class);
			Supplier<String> resilientFetch = CircuitBreaker.decorateSupplier(circuitBreaker,
				Retry.decorateSupplier(retry, fetch));

			String json = resilientFetch.get();
			JwkSet jwkSet = Jwks.setParser().build().parse(json);
			keysByKid = jwkSet.getKeys().stream()
				.collect(Collectors.toMap(Jwk::getId, Jwk::toKey));
			lastFetchedAt = Instant.now();
			log.info("JWKS 갱신 완료: kid={}", keysByKid.keySet());
		} catch (Exception e) {
			log.error("JWKS 갱신 실패, 기존 캐시({}개 키)를 유지한다", keysByKid.size(), e);
		} finally {
			lock.unlock();
		}
	}
}
