package com.groovy.backend.calendar.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groovy.backend.observability.TracingConfig;
import com.sun.net.httpserver.HttpServer;

/**
 * IR-315 / IR-310: calendar-service -> study-service 호출에 W3C traceparent가 실제로 실리는지
 * 검증한다. StudyServiceClient가 RestClient.builder() 정적 팩토리 대신 DI로 받은
 * RestClient.Builder를 쓰는지가 이 테스트의 핵심 — 정적 팩토리로 되돌아가면 이 테스트가
 * 바로 깨진다.
 *
 * 전체 앱 컨텍스트(@SpringBootTest)는 DB/Kafka가 필요해 여기선 안 쓰고, ApplicationContextRunner로
 * RestClient observation 배선 + 실제 프로덕션 TracingConfig만 골라 띄운다.
 */
class StudyServiceClientTracePropagationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer stubServer;

	@AfterEach
	void tearDown() {
		if (stubServer != null) {
			stubServer.stop(0);
		}
	}

	@Test
	void 나가는_요청에_W3C_traceparent_헤더가_실린다() throws Exception {
		AtomicReference<String> capturedTraceparent = new AtomicReference<>();
		int port = startStubServerCapturingTraceparent("/api/studies/10", capturedTraceparent);

		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
				RestClientAutoConfiguration.class,
				RestClientObservationAutoConfiguration.class,
				ObservationAutoConfiguration.class,
				MicrometerTracingAutoConfiguration.class,
				OpenTelemetrySdkAutoConfiguration.class))
			.withUserConfiguration(TracingConfig.class)
			.withPropertyValues(
				"management.otlp.tracing.endpoint=http://localhost:4318/v1/traces",
				"spring.application.name=calendar-service")
			.run(context -> {
				RestClient.Builder restClientBuilder = context.getBean(RestClient.Builder.class);
				StudyServiceClient client = new StudyServiceClient(restClientBuilder, "http://localhost:" + port, 2000, 3000);
				client.getStudy(10L);
			});

		String traceparent = capturedTraceparent.get();
		assertThat(traceparent).isNotNull();
		assertThat(traceparent).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");
	}

	private int startStubServerCapturingTraceparent(String path, AtomicReference<String> traceparentHolder) throws Exception {
		String responseJson = objectMapper.writeValueAsString(Map.of(
			"status", "SUCCESS",
			"message", "스터디 조회에 성공했습니다.",
			"data", Map.of("id", "10", "leaderId", "1", "title", "알고리즘 스터디", "myApplicationStatus", "APPROVED")
		));
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext(path, exchange -> {
			traceparentHolder.set(exchange.getRequestHeaders().getFirst("traceparent"));
			byte[] bytes = responseJson.getBytes();
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		this.stubServer = server;
		TimeUnit.MILLISECONDS.sleep(50);
		return server.getAddress().getPort();
	}
}
