package com.groovy.backend.calendar.outbox;

import java.time.LocalDateTime;

import com.groovy.backend.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MSA 전환(calendar-service 추출): groovy(레거시)의 Transactional Outbox 패턴을 그대로 재사용한다
 * (Phase 9, groovy/.../global/outbox/OutboxEvent.java 참고). calendar-service가 일정 변경
 * 알림을 자기 트랜잭션 안에서 이 테이블에 기록하고, OutboxRelay가 Kafka로 발행한다.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true, length = 36)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 50)
	private String eventType;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Builder
	public OutboxEvent(String eventId, String eventType, String payload) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.payload = payload;
		this.published = false;
	}

	public void markPublished() {
		this.published = true;
		this.publishedAt = LocalDateTime.now();
	}
}
