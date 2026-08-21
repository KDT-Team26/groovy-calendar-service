package com.groovy.backend.calendar.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.calendar.Calendar;
import com.groovy.backend.calendar.client.StudyServiceClient;
import com.groovy.backend.calendar.client.StudyServiceClient.StudyOptionView;
import com.groovy.backend.calendar.client.StudyServiceClient.StudyView;
import com.groovy.backend.calendar.dto.CalendarCreateRequest;
import com.groovy.backend.calendar.dto.CalendarEventResponse;
import com.groovy.backend.calendar.dto.CalendarUpdateRequest;
import com.groovy.backend.calendar.dto.MyStudyOptionResponse;
import com.groovy.backend.common.exception.ForbiddenException;
import com.groovy.backend.calendar.notification.NotificationOutboxPublisher;
import com.groovy.backend.calendar.notification.ScheduleChangeType;
import com.groovy.backend.calendar.repository.CalendarRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환(calendar-service 추출): groovy(레거시)의 CalendarService를 그대로 옮기되, principal이
 * email이 아니라 userId(Long)로 바뀌었다 — 이 서비스에는 User 테이블이 없어서 email로 조회할
 * 방법이 없고, JWT의 uid 클레임이 이미 신원 그 자체다(study-service와 동일한 패턴). Study 조회는
 * groovy에서 이미 도입된 StudyServiceClient(동기 HTTP)를 그대로 복사해 재사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

	private final CalendarRepository calendarRepository;
	private final StudyServiceClient studyServiceClient;
	private final NotificationOutboxPublisher notificationOutboxPublisher;

	@Transactional
	public CalendarEventResponse addSchedule(Long userId, CalendarCreateRequest request) {
		StudyView study = request.studyId() != null ? requireMembership(userId, request.studyId()) : null;

		LocalDate startDate = request.startDate();
		LocalDate endDate = request.endDate() != null ? request.endDate() : startDate;
		validatePeriod(startDate, endDate);

		Calendar calendar = Calendar.builder()
			.userId(userId)
			.studyId(study != null ? Long.valueOf(study.id()) : null)
			.title(request.title())
			.content(request.content())
			.startDate(startDate)
			.endDate(endDate)
			.build();

		Calendar saved = calendarRepository.save(calendar);
		log.info("일정 등록: userId={}, studyId={}, calendarId={}", userId, request.studyId(), saved.getId());

		if (study == null) {
			return CalendarEventResponse.forPersonal(saved, userId);
		}

		notifyStudyMembers(study, userId, saved.getTitle(), ScheduleChangeType.CREATED);
		return CalendarEventResponse.forStudy(saved, study.title(), isLeader(study, userId));
	}

	public CalendarEventResponse getSchedule(Long userId, Long id) {
		Calendar calendar = findCalendarOrThrow(id);

		if (calendar.isPersonal()) {
			assertOwnsPersonal(userId, calendar);
			return CalendarEventResponse.forPersonal(calendar, userId);
		}

		StudyView study = getStudyOrThrow(calendar.getStudyId());
		assertStudyMember(userId, study);
		return CalendarEventResponse.forStudy(calendar, study.title(), isLeader(study, userId));
	}

	@Transactional
	public CalendarEventResponse updateSchedule(Long userId, Long id, CalendarUpdateRequest request) {
		Calendar calendar = findCalendarOrThrow(id);
		StudyView study = calendar.isPersonal() ? null : getStudyOrThrow(calendar.getStudyId());
		assertManageable(userId, calendar, study);

		LocalDate startDate = request.startDate();
		LocalDate endDate = request.endDate() != null ? request.endDate() : startDate;
		validatePeriod(startDate, endDate);

		calendar.update(request.title(), request.content(), startDate, endDate);
		log.info("일정 수정: userId={}, calendarId={}", userId, id);

		if (study == null) {
			return CalendarEventResponse.forPersonal(calendar, userId);
		}

		notifyStudyMembers(study, userId, calendar.getTitle(), ScheduleChangeType.UPDATED);
		return CalendarEventResponse.forStudy(calendar, study.title(), isLeader(study, userId));
	}

	@Transactional
	public void deleteSchedule(Long userId, Long id) {
		Calendar calendar = findCalendarOrThrow(id);
		StudyView study = calendar.isPersonal() ? null : getStudyOrThrow(calendar.getStudyId());
		assertManageable(userId, calendar, study);

		String title = calendar.getTitle();
		calendarRepository.delete(calendar);
		log.info("일정 삭제: userId={}, calendarId={}", userId, id);

		if (study != null) {
			notifyStudyMembers(study, userId, title, ScheduleChangeType.DELETED);
		}
	}

	/**
	 * Step A: 개인 일정을 조회하고, Step B: 내가 속한(방장이거나 승인된) 스터디들의 약속을 조회한 뒤,
	 * Step C: 두 목록을 병합/정렬하여 반환한다.
	 */
	public List<CalendarEventResponse> getIntegratedCalendar(Long userId) {
		List<Calendar> personalSchedules = calendarRepository.findByUserIdAndStudyIdIsNull(userId);

		Map<Long, StudyOptionView> myStudyById = studyServiceClient.getMyStudyOptions().stream()
			.collect(Collectors.toMap(option -> Long.valueOf(option.studyId()), option -> option));
		List<Calendar> studySchedules = myStudyById.isEmpty()
			? List.of()
			: calendarRepository.findByStudyIdIn(List.copyOf(myStudyById.keySet()));

		Stream<CalendarEventResponse> personalEvents = personalSchedules.stream()
			.map(calendar -> CalendarEventResponse.forPersonal(calendar, userId));
		Stream<CalendarEventResponse> studyEvents = studySchedules.stream()
			.map(calendar -> {
				StudyOptionView option = myStudyById.get(calendar.getStudyId());
				return CalendarEventResponse.forStudy(calendar, option.title(), option.isLeader());
			});

		return Stream.concat(personalEvents, studyEvents)
			.sorted(Comparator.comparing(CalendarEventResponse::startDate))
			.toList();
	}

	public List<MyStudyOptionResponse> getMyStudyOptions(Long userId) {
		return studyServiceClient.getMyStudyOptions().stream()
			.map(MyStudyOptionResponse::from)
			.toList();
	}

	// study-service를 호출해 스터디가 실제로 존재하는지 + 이 유저가 방장이거나 승인된 멤버인지
	// 확인한다("/api/studies/{id}"의 myApplicationStatus를 재사용, 별도 멤버십 API를 만들지 않음).
	private StudyView requireMembership(Long userId, Long studyId) {
		StudyView study = getStudyOrThrow(studyId);

		if (!isMember(study, userId)) {
			log.warn("스터디 멤버 아님: studyId={}, userId={}", studyId, userId);
			throw new ForbiddenException("스터디 멤버만 약속을 등록할 수 있습니다.");
		}

		return study;
	}

	private StudyView getStudyOrThrow(Long studyId) {
		return studyServiceClient.getStudy(studyId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});
	}

	private Calendar findCalendarOrThrow(Long id) {
		return calendarRepository.findById(id)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 일정: calendarId={}", id);
				return new IllegalArgumentException("존재하지 않는 일정입니다.");
			});
	}

	// 개인 일정은 작성자 본인만 조회할 수 있다.
	private void assertOwnsPersonal(Long userId, Calendar calendar) {
		if (!calendar.getUserId().equals(userId)) {
			log.warn("개인 일정 조회 권한 없음: userId={}, calendarId={}", userId, calendar.getId());
			throw new ForbiddenException("본인의 개인 일정만 조회할 수 있습니다.");
		}
	}

	// 스터디 일정은 방장이거나 승인된 멤버만 조회할 수 있다.
	private void assertStudyMember(Long userId, StudyView study) {
		if (!isMember(study, userId)) {
			log.warn("스터디 일정 조회 권한 없음: userId={}, studyId={}", userId, study.id());
			throw new ForbiddenException("스터디 멤버만 그룹 일정을 조회할 수 있습니다.");
		}
	}

	// 개인 일정은 작성자 본인만, 스터디 일정은 방장만 수정/삭제할 수 있다(작성자가 누구든 무관).
	private void assertManageable(Long userId, Calendar calendar, StudyView study) {
		if (calendar.isPersonal()) {
			if (!calendar.getUserId().equals(userId)) {
				log.warn("개인 일정 수정/삭제 권한 없음: userId={}, calendarId={}", userId, calendar.getId());
				throw new ForbiddenException("본인의 개인 일정만 수정/삭제할 수 있습니다.");
			}
			return;
		}

		if (!isLeader(study, userId)) {
			log.warn("스터디 일정 수정/삭제 권한 없음: userId={}, studyId={}", userId, study.id());
			throw new ForbiddenException("스터디 방장만 그룹 일정을 수정/삭제할 수 있습니다.");
		}
	}

	// 방장 + 승인된 멤버 전원(행위자 본인 제외)에게 스터디 일정 변경을 알린다.
	private void notifyStudyMembers(StudyView study, Long actorUserId, String scheduleTitle, ScheduleChangeType changeType) {
		Long studyId = Long.valueOf(study.id());
		List<Long> recipientUserIds = Stream.concat(
				Stream.of(Long.valueOf(study.leaderId())),
				studyServiceClient.getApprovedMemberUserIds(studyId).stream()
			)
			.filter(userId -> !userId.equals(actorUserId))
			.distinct()
			.toList();

		if (recipientUserIds.isEmpty()) {
			return;
		}

		notificationOutboxPublisher.studyScheduleChanged(
			recipientUserIds, studyId, study.title(), scheduleTitle, changeType);
	}

	private boolean isMember(StudyView study, Long userId) {
		return isLeader(study, userId) || "APPROVED".equals(study.myApplicationStatus());
	}

	private boolean isLeader(StudyView study, Long userId) {
		return String.valueOf(userId).equals(study.leaderId());
	}

	private void validatePeriod(LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) {
			log.warn("잘못된 일정 기간: startDate={}, endDate={}", startDate, endDate);
			throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
		}
	}
}
