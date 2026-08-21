package com.groovy.backend.calendar.outbox;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

	static final String TOPIC = "notification-events";
	private static final long SEND_ACK_TIMEOUT_SECONDS = 3;

	private final OutboxEventRepository outboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	@Scheduled(fixedDelay = 1000)
	public void relay() {
		List<OutboxEvent> batch = outboxEventRepository.findTop50ByPublishedFalseOrderByIdAsc();

		for (OutboxEvent event : batch) {
			try {
				kafkaTemplate.send(TOPIC, event.getEventId(), event.getPayload())
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
}
