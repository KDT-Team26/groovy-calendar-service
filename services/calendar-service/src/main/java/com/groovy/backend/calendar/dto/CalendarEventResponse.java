package com.groovy.backend.calendar.dto;

import com.groovy.backend.calendar.Calendar;
import com.groovy.backend.calendar.CalendarSourceType;

/**
 * 개인 일정과 스터디 약속(둘 다 Calendar 엔티티)을 프론트엔드 캘린더 월간 뷰의
 * CalendarEvent 타입에 맞춰 내려주는 응답 DTO.
 */
public record CalendarEventResponse(
	String id,
	// 목록 렌더링용 합성 id(id)와 별개로, 상세조회/수정/삭제 API 경로에 쓰는 원본 PK.
	String calendarId,
	String title,
	String content,
	String startDate,
	String endDate,
	String studyId,
	String studyTitle,
	CalendarSourceType type,
	// 요청자가 이 일정을 수정/삭제할 수 있는지. 개인 일정은 작성자 본인, 스터디 일정은 방장만 true.
	boolean canManage
) {

	public static CalendarEventResponse forPersonal(Calendar calendar, Long requesterId) {
		boolean canManage = calendar.getUserId().equals(requesterId);
		return new CalendarEventResponse(
			"personal-" + calendar.getId(),
			String.valueOf(calendar.getId()),
			calendar.getTitle(),
			calendar.getContent(),
			calendar.getStartDate().toString(),
			calendar.getEndDate().toString(),
			null,
			null,
			CalendarSourceType.PERSONAL,
			canManage
		);
	}

	// study 상세 정보(title, 방장 여부)는 다른 Bounded Context(study-service)의 데이터라, Calendar가
	// 직접 들고 있지 않고 호출부(CalendarService)가 StudyServiceClient로 조회한 뒤 넘겨준다.
	public static CalendarEventResponse forStudy(Calendar calendar, String studyTitle, boolean canManage) {
		return new CalendarEventResponse(
			"study-" + calendar.getId(),
			String.valueOf(calendar.getId()),
			calendar.getTitle(),
			calendar.getContent(),
			calendar.getStartDate().toString(),
			calendar.getEndDate().toString(),
			String.valueOf(calendar.getStudyId()),
			studyTitle,
			CalendarSourceType.STUDY,
			canManage
		);
	}
}
