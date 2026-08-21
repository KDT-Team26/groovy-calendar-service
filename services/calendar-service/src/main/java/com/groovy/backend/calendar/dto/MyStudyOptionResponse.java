package com.groovy.backend.calendar.dto;

import com.groovy.backend.calendar.client.StudyServiceClient.StudyOptionView;

/**
 * 캘린더에서 "스터디 약속" 등록 시 내가 속한 스터디를 고르기 위한 최소 정보.
 */
public record MyStudyOptionResponse(
	String studyId,
	String title
) {

	public static MyStudyOptionResponse from(StudyOptionView option) {
		return new MyStudyOptionResponse(option.studyId(), option.title());
	}
}
