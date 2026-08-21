package com.groovy.backend.calendar.notification;

import java.util.List;

import org.springframework.stereotype.Component;

import com.groovy.backend.calendar.outbox.OutboxEventWriter;
import com.groovy.backend.eventcontract.notification.NotificationPayload;

import lombok.RequiredArgsConstructor;

/**
 * MSA 전환(calendar-service 추출): groovy(레거시)의 NotificationOutboxPublisher에서 Calendar가
 * 쓰던 studyScheduleChanged 하나만 옮겨온다. Memoir가 쓰던 나머지 메서드(memoirCommentAdded,
 * memoirLikeAdded)는 groovy에 그대로 남아있다(Memoir가 아직 groovy에 있으므로).
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

	private final OutboxEventWriter outboxEventWriter;

	public void studyScheduleChanged(List<Long> recipientUserIds, Long studyId, String studyTitle,
		String scheduleTitle, ScheduleChangeType changeType) {
		String title = switch (changeType) {
			case CREATED -> "새 일정이 등록됐어요";
			case UPDATED -> "일정이 변경됐어요";
			case DELETED -> "일정이 삭제됐어요";
		};
		String actionVerb = switch (changeType) {
			case CREATED -> "등록됐어요";
			case UPDATED -> "변경됐어요";
			case DELETED -> "삭제됐어요";
		};
		for (Long recipientUserId : recipientUserIds) {
			publish(recipientUserId, "STUDY_SCHEDULE_CHANGED", title,
				"\"%s\"의 \"%s\" 일정이 %s".formatted(studyTitle, scheduleTitle, actionVerb), studyId);
		}
	}

	private void publish(Long recipientUserId, String type, String title, String message, Long targetId) {
		outboxEventWriter.write(type, new NotificationPayload(recipientUserId, type, title, message, targetId));
	}
}
