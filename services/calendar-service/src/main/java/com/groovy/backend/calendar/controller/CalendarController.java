package com.groovy.backend.calendar.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groovy.backend.calendar.dto.CalendarCreateRequest;
import com.groovy.backend.calendar.dto.CalendarEventResponse;
import com.groovy.backend.calendar.dto.CalendarUpdateRequest;
import com.groovy.backend.calendar.dto.MyStudyOptionResponse;
import com.groovy.backend.calendar.service.CalendarService;
import com.groovy.backend.common.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/calendars")
@RequiredArgsConstructor
public class CalendarController {

	private final CalendarService calendarService;

	@GetMapping
	public ApiResponse<List<CalendarEventResponse>> getCalendars(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of("SUCCESS", "통합 일정 조회에 성공했습니다.", calendarService.getIntegratedCalendar(userId));
	}

	// 캘린더에서 "스터디 약속" 등록 시 고를 수 있는, 내가 방장이거나 승인되어 속한 스터디 목록.
	@GetMapping("/studies")
	public ApiResponse<List<MyStudyOptionResponse>> getMyStudyOptions(@AuthenticationPrincipal Long userId) {
		return ApiResponse.of("SUCCESS", "내 스터디 목록 조회에 성공했습니다.", calendarService.getMyStudyOptions(userId));
	}

	@PostMapping
	public ApiResponse<CalendarEventResponse> addCalendar(
		@AuthenticationPrincipal Long userId,
		@Valid @RequestBody CalendarCreateRequest request
	) {
		return ApiResponse.of("SUCCESS", "일정이 추가되었습니다.", calendarService.addSchedule(userId, request));
	}

	@GetMapping("/{id}")
	public ApiResponse<CalendarEventResponse> getCalendar(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
		return ApiResponse.of("SUCCESS", "일정 상세 조회에 성공했습니다.", calendarService.getSchedule(userId, id));
	}

	@PutMapping("/{id}")
	public ApiResponse<CalendarEventResponse> updateCalendar(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long id,
		@Valid @RequestBody CalendarUpdateRequest request
	) {
		return ApiResponse.of("SUCCESS", "일정이 수정되었습니다.", calendarService.updateSchedule(userId, id, request));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> deleteCalendar(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
		calendarService.deleteSchedule(userId, id);
		return ApiResponse.of("SUCCESS", "일정이 삭제되었습니다.");
	}
}
