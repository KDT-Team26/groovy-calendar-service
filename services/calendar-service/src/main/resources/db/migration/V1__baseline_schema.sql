-- MSA 전환(calendar-service 추출, Phase 7 DB per Service 패턴 재사용): groovy(레거시)의
-- calendars/outbox_events를 calendar_db 스키마로 옮겨온다. user_id는 identity-service 소속
-- (User Aggregate), study_id는 study-service 소속(Study Aggregate)이라 이 스키마에 FK를 걸지
-- 않는다(study_db의 leader_id/applicant_id, notification_db의 recipient_user_id와 동일한 이유).
-- description/start_time/end_time 컬럼은 groovy V2에서 이미 드롭됐고, content는 groovy V5에서
-- 추가됐다 — 최종 형태만 그대로 옮긴다.

CREATE TABLE `calendars` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `content` TEXT NULL,
  `user_id` bigint NOT NULL,
  `date` date NOT NULL,
  `end_date` date NOT NULL,
  `study_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_calendars_user_id` (`user_id`),
  KEY `idx_calendars_study_id` (`study_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `outbox_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` varchar(36) NOT NULL,
  `event_type` varchar(50) NOT NULL,
  `payload` TEXT NOT NULL,
  `published` boolean NOT NULL DEFAULT false,
  `published_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_unpublished` (`published`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
