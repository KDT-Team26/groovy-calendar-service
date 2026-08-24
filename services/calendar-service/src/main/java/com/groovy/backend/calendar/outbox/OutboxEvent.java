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

	// IR-312: 접수 시점(원래 HTTP 요청 스레드, 도메인 쓰기와 같은 트랜잭션)의 W3C traceparent.
	// OutboxRelay가 이 값을 그대로 Kafka record header로 옮겨야 배치 발행 이후에도 원래 요청까지
	// trace가 이어진다 — 이게 없으면 릴레이 스레드가 매번 새 trace를 시작해버린다.
	@Column(name = "trace_parent", length = 55)
	private String traceParent;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	@Builder
	public OutboxEvent(String eventId, String eventType, String payload, String traceParent) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.payload = payload;
		this.traceParent = traceParent;
		this.published = false;
	}

	public void markPublished() {
		this.published = true;
		this.publishedAt = LocalDateTime.now();
	}
}
