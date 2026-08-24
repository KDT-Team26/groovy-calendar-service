package com.groovy.backend.calendar.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.groovy.backend.calendar.config.TracingConfig;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/**
 * IR-315 / IR-312: OutboxEventWriter가 접수 시점(원래 요청 스레드)의 W3C traceparent를 도메인
 * 데이터와 함께 저장하는지 검증한다. 이게 안 되면 OutboxRelay가 배치로 발행할 때 원래 요청과
 * trace가 끊긴다(다이어그램/설명은 IR-312 작업 참고).
 *
 * DB/Kafka는 안 쓰고 repository만 Mockito로 목킹, Tracer/Propagator만 ApplicationContextRunner로
 * 실제 프로덕션 TracingConfig에서 가져온다.
 */
class OutboxEventWriterTracePropagationTest {

	@Test
	void write는_현재_span의_traceparent를_같이_저장한다() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
				ObservationAutoConfiguration.class,
				MicrometerTracingAutoConfiguration.class,
				OpenTelemetrySdkAutoConfiguration.class))
			.withUserConfiguration(TracingConfig.class)
			.withPropertyValues(
				"management.otlp.tracing.endpoint=http://localhost:4318/v1/traces",
				"spring.application.name=calendar-service")
			.run(context -> {
				Tracer tracer = context.getBean(Tracer.class);
				Propagator propagator = context.getBean(Propagator.class);

				OutboxEventRepository repository = mock(OutboxEventRepository.class);
				OutboxEventWriter writer = new OutboxEventWriter(repository, new ObjectMapper().registerModule(new JavaTimeModule()), tracer, propagator);

				Span span = tracer.nextSpan().name("test-request").start();
				String expectedTraceId = span.context().traceId();
				try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
					writer.write("CALENDAR_EVENT_CREATED", Map.of("studyId", 1));
				} finally {
					span.end();
				}

				ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
				verify(repository).save(captor.capture());
				String traceParent = captor.getValue().getTraceParent();

				assertThat(traceParent).isNotNull();
				assertThat(traceParent).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");
				assertThat(traceParent).contains(expectedTraceId);
			});
	}

	@Test
	void 활성_span이_없으면_traceParent는_null이다() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
				ObservationAutoConfiguration.class,
				MicrometerTracingAutoConfiguration.class,
				OpenTelemetrySdkAutoConfiguration.class))
			.withUserConfiguration(TracingConfig.class)
			.withPropertyValues(
				"management.otlp.tracing.endpoint=http://localhost:4318/v1/traces",
				"spring.application.name=calendar-service")
			.run(context -> {
				Tracer tracer = context.getBean(Tracer.class);
				Propagator propagator = context.getBean(Propagator.class);

				OutboxEventRepository repository = mock(OutboxEventRepository.class);
				OutboxEventWriter writer = new OutboxEventWriter(repository, new ObjectMapper().registerModule(new JavaTimeModule()), tracer, propagator);

				writer.write("CALENDAR_EVENT_CREATED", Map.of("studyId", 1));

				ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
				verify(repository).save(captor.capture());
				assertThat(captor.getValue().getTraceParent()).isNull();
			});
	}
}
