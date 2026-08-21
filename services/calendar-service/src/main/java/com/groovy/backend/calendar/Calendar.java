package com.groovy.backend.calendar;

import java.time.LocalDate;

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

@Entity
@Table(name = "calendars")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Calendar extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// user는 User Aggregate(identity-service 소속)라 JPA 연관관계(엔티티 참조)로 들고 있지 않고
	// FK 값만 저장한다(바로 아래 studyId와 같은 이유·같은 방식).
	@Column(name = "user_id", nullable = false)
	private Long userId;

	// null이면 작성자 개인 일정, 값이 있으면 해당 스터디 멤버 전원에게 공유되는 스터디 약속이다.
	// Study는 다른 Bounded Context(study-service)의 Aggregate이므로 JPA 연관관계(엔티티 참조)로
	// 들고 있지 않고 FK 값만 저장한다. 제목/방장 등 상세 정보가 필요하면 StudyServiceClient로
	// 조회한다.
	@Column(name = "study_id")
	private Long studyId;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String content;

	// 캘린더 UI가 날짜 단위 그리드(월간 뷰)로만 일정을 다루므로 시간 없이 날짜만 저장한다.
	// DB 컬럼명은 과거 단일 날짜 시절의 "date"를 그대로 쓰되, 의미상 기간의 시작일이다.
	@Column(name = "date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Builder
	public Calendar(Long userId, Long studyId, String title, String content, LocalDate startDate, LocalDate endDate) {
		this.userId = userId;
		this.studyId = studyId;
		this.title = title;
		this.content = content;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public void update(String title, String content, LocalDate startDate, LocalDate endDate) {
		this.title = title;
		this.content = content;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public boolean isPersonal() {
		return this.studyId == null;
	}
}
