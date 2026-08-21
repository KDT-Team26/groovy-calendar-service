package com.groovy.backend.calendar.notification;

// groovy(레거시)의 global.notification.ScheduleChangeType과 동일 — 일정 변경 종류를 표현하는
// 값 타입. Calendar가 이 서비스로 옮겨오면서 함께 이관했다(groovy에는 더 이상 필요 없음).
public enum ScheduleChangeType {
	CREATED, UPDATED, DELETED
}
