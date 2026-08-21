package com.groovy.backend.eventcontract.notification;

/**
 * study/calendar/content-service가 발행하고 notification-service가 소비하는 알림 이벤트의
 * payload. 제목/본문은 발행하는 서비스가 이미 렌더링해서 보낸다 — notification-service는
 * 도메인 의미를 몰라도 저장+push만 하면 된다.
 */
public record NotificationPayload(
	Long recipientUserId,
	String type,
	String title,
	String message,
	Long targetId
) {
}
