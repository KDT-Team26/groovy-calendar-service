package com.groovy.backend.calendar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groovy.backend.calendar.Calendar;

public interface CalendarRepository extends JpaRepository<Calendar, Long> {

	List<Calendar> findByUserIdAndStudyIdIsNull(Long userId);

	// study가 JPA 연관관계가 아니라 studyId 뿐이라 fetch join이 필요 없다. 호출부(CalendarService)가
	// 이미 내 스터디 목록을 한 번에 조회해 Map으로 들고 있으므로 N+1 문제가 애초에 발생하지 않는다.
	List<Calendar> findByStudyIdIn(List<Long> studyIds);
}
