package com.groovy.backend.calendar.outbox;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groovy.backend.eventcontract.EventEnvelope;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;

/**
 * MSA 전환(calendar-service 추출): groovy(레거시)의 OutboxEventWriter와 동일한 패턴(Phase 9).
 * 호출하는 도메인 서비스 메서드 자신의 @Transactional 안에서 호출돼야 원자성이 보장된다.
 *
 * IR-312: write()는 항상 원래 HTTP 요청 스레드 안에서 호출되므로, 이 시점의 W3C traceparent를
 * Propagator로 뽑아 도메인 데이터와 같은 트랜잭션으로 저장한다. OutboxRelay는 별도 스케줄러
 * 스레드라 이 시점의 trace context를 다시 만들어낼 방법이 없기 때문에, 여기서 저장해두는 것만이
 * 유일한 전달 수단이다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;
	private final Tracer tracer;
	private final Propagator propagator;

	public void write(String eventType, Object payload) {
		EventEnvelope<Object> envelope = EventEnvelope.of(eventType, payload);
		String eventId = envelope.eventId().toString();

		try {
			String json = objectMapper.writeValueAsString(envelope);
			outboxEventRepository.save(OutboxEvent.builder()
				.eventId(eventId)
				.eventType(eventType)
				.payload(json)
				.traceParent(currentTraceParent())
				.build());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Outbox 이벤트 직렬화 실패: eventType=" + eventType, e);
		}
	}

	// 현재 span이 없으면(예: 배치/초기화 컨텍스트에서 write가 불릴 경우) null — OutboxRelay가
	// null이면 header 주입을 건너뛰도록 처리한다.
	private String currentTraceParent() {
		if (tracer.currentSpan() == null) {
			return null;
		}
		Map<String, String> carrier = new HashMap<>();
		propagator.inject(tracer.currentSpan().context(), carrier, Map::put);
		return carrier.get("traceparent");
	}
}
