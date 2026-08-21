package com.groovy.backend.calendar.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CalendarUpdateRequest(

	@NotBlank(message = "일정 제목은 필수입니다.")
	String title,

	String content,

	@NotNull(message = "시작일은 필수입니다.")
	LocalDate startDate,

	// null이면 startDate와 같은 하루짜리 일정으로 수정된다.
	LocalDate endDate
) {
}
