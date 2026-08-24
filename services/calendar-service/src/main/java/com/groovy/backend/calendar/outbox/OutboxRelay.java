package com.groovy.backend.calendar.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환(calendar-service 추출): groovy(레거시)의 OutboxRelay와 동일한 패턴(Phase 9). 이
 * 서비스가 발행하는 이벤트도 notification-service가 소비하는 "notification-events" 토픽으로
 * 나간다 — 여러 프로듀서가 같은 토픽에 쓰는 건 정상이다(notification-service는 발행자가
 * 누구인지 몰라도 됨).
 *
 * IR-312: 이 메서드는 @Scheduled 스레드라 원래 요청의 trace context를 ambient하게 이어받을 방법이
 * 없다. 그래서 자동 계측(spring.kafka.template.observation-enabled) 대신, OutboxEventWriter가
 * 저장해둔 traceParent를 ProducerRecord header에 수동으로 심는다 — 자동 계측을 같이 켜면 이 시점
 * (배치 스레드)의 ambient context로 header가 덮어써질 수 있어 일부러 켜지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

	static final String TOPIC = "notification-events";
	private static final String TRACEPARENT_HEADER = "traceparent";
	private static final long SEND_ACK_TIMEOUT_SECONDS = 3;

	private final OutboxEventRepository outboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	@Scheduled(fixedDelay = 1000)
	public void relay() {
		List<OutboxEvent> batch = outboxEventRepository.findTop50ByPublishedFalseOrderByIdAsc();

		for (OutboxEvent event : batch) {
			try {
				kafkaTemplate.send(toProducerRecord(event))
					.get(SEND_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
				event.markPublished();
				outboxEventRepository.save(event);
				log.info("Outbox 이벤트 발행 성공: eventId={}, eventType={}", event.getEventId(), event.getEventType());
			} catch (Exception e) {
				log.warn("Outbox 이벤트 발행 실패, 다음 폴링에서 재시도: eventId={}, eventType={}",
					event.getEventId(), event.getEventType(), e);
				return;
			}
		}
	}

	private ProducerRecord<String, String> toProducerRecord(OutboxEvent event) {
		ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, event.getEventId(), event.getPayload());
		if (event.getTraceParent() != null) {
			record.headers().add(new RecordHeader(TRACEPARENT_HEADER, event.getTraceParent().getBytes(StandardCharsets.UTF_8)));
		}
		return record;
	}
}
