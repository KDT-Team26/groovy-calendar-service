package com.groovy.backend.client;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 서비스 간 동기 HTTP 호출(RestClient)을 CircuitBreaker+Retry로 감싸는 공용 실행기.
 * JwksKeyLocator(libs:security-common)가 이미 쓰던 것과 같은 resilience4j 조합을 비즈니스
 * 클라이언트(UserServiceClient/StudyServiceClient/TagPreferenceClient)에도 적용하기 위해
 * 만들었다 — 클라이언트마다 설정을 손으로 반복하지 않게 한다.
 *
 * 다운스트림 서비스가 다운이 아니라 "느려지기만" 해도 매 요청이 read-timeout만큼(기본 3초)
 * 블로킹되는 문제를 막는 게 목적이다: 반복 실패 시 회로를 열어 이후 호출은 타임아웃을
 * 기다리지 않고 즉시 fallback으로 넘어간다.
 *
 * Retry는 전송 실패(연결 끊김/타임아웃, ResourceAccessException)와 5xx에만 건다 — 4xx(예:
 * "해당 리소스 없음")는 재시도해도 결과가 똑같으므로 재시도하지 않는다. CircuitBreaker도 4xx는
 * 실패로 집계하지 않는다 — 존재하지 않는 리소스를 조회한 것뿐이지 다운스트림 장애 신호가
 * 아니기 때문이다.
 */
@Slf4j
public class ResilientCallExecutor {

	private final String name;
	private final CircuitBreaker circuitBreaker;
	private final Retry retry;

	public ResilientCallExecutor(String name) {
		this.name = name;

		this.circuitBreaker = CircuitBreaker.of(name, CircuitBreakerConfig.custom()
			.failureRateThreshold(50)
			.slidingWindowSize(5)
			.minimumNumberOfCalls(3)
			.waitDurationInOpenState(Duration.ofSeconds(10))
			.permittedNumberOfCallsInHalfOpenState(1)
			.ignoreExceptions(HttpClientErrorException.class)
			.build());
		this.circuitBreaker.getEventPublisher().onStateTransition(
			event -> log.warn("{} CircuitBreaker 상태 전이: {}", name, event.getStateTransition()));

		this.retry = Retry.of(name, RetryConfig.custom()
			.maxAttempts(2)
			.intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(200), 2.0))
			.retryExceptions(ResourceAccessException.class, HttpServerErrorException.class)
			.build());
		this.retry.getEventPublisher().onRetry(
			event -> log.warn("{} 호출 재시도: attempt={}, lastError={}",
				name, event.getNumberOfRetryAttempts(), event.getLastThrowable().toString()));
	}

	/**
	 * call을 CircuitBreaker+Retry로 감싸 실행하고, 실패하면(회로 열림/재시도 소진/4xx 등)
	 * fallback을 대신 반환한다. "예외를 삼키고 대체 값을 반환"하는 fail-open 정책 자체는
	 * 기존 클라이언트들과 동일하게 유지한다 — 이 클래스가 바꾸는 건 "실패를 얼마나 빨리 판단해
	 * fallback으로 넘어가느냐"이다.
	 */
	public <T> T execute(Supplier<T> call, Supplier<T> fallback) {
		try {
			Supplier<T> decorated = CircuitBreaker.decorateSupplier(circuitBreaker,
				Retry.decorateSupplier(retry, call));
			return decorated.get();
		} catch (Exception e) {
			log.error("{} 호출 실패, fallback으로 대체: {}", name, e.toString());
			return fallback.get();
		}
	}

	/** 반환값이 없는 호출(예: POST)을 위한 오버로드. */
	public void execute(Runnable call, Runnable onFailure) {
		execute(
			() -> {
				call.run();
				return null;
			},
			() -> {
				onFailure.run();
				return null;
			});
	}
}
