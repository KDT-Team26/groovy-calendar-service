package com.groovy.backend.calendar.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CalendarCreateRequest(

	@NotBlank(message = "일정 제목은 필수입니다.")
	String title,

	// 상세 조회 편의를 위한 본문. 선택 항목이라 null일 수 있다.
	String content,

	@NotNull(message = "시작일은 필수입니다.")
	LocalDate startDate,

	// null이면 startDate와 같은 하루짜리 일정으로 등록된다.
	LocalDate endDate,

	// null이면 개인 일정, 값이 있으면 해당 스터디 멤버 전원과 공유되는 스터디 약속으로 등록된다.
	Long studyId
) {
}
