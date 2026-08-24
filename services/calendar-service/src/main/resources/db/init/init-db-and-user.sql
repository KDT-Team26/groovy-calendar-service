-- MSA 전환(calendar-service 추출, Phase 7 DB per Service 패턴 재사용): calendar_db 스키마 + 전용
-- 계정을 추가한다.
--
-- 소유권 이전(groovy-infra#7): 이 스크립트는 원래 groovy-infra/mysql-init/04-calendar-service.sql로
-- platform(mysql)이 소유하고 있었으나, platform이 서비스 Secret을 역참조하는 문제를 없애기 위해
-- 이 레포로 이전했다. 실행 메커니즘(누가/언제 이 스크립트를 돌릴지)은 아직 연결되지 않았고
-- 후속 이슈에서 다룬다 — 지금은 파일 소유권 이전까지만이 이번 작업 범위다.

CREATE DATABASE IF NOT EXISTS calendar_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'calendar_service'@'%'
  IDENTIFIED BY 'calendar_service_local_only_pw';

-- calendar_service 계정은 calendar_db에만 권한이 있다. groovy_db/study_db/identity_db에는
-- 아무 권한도 주지 않는다.
GRANT ALL PRIVILEGES ON calendar_db.* TO 'calendar_service'@'%';

FLUSH PRIVILEGES;
