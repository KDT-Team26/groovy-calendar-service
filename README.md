# groovy-calendar-service

## 1. 이 레포는 무엇인가

**Groovy**는 태그 기반으로 스터디 그룹을 매칭하고, 참여 신청/승인, 캘린더 일정 관리, 회고록 공유,
실시간 알림까지 지원하는 스터디 커뮤니티 플랫폼입니다. 백엔드는 하나의 Spring Boot 모놀리스에서
도메인별 마이크로서비스로 전환되었고, 지금은 서비스별 폴리레포로 분리되는 중입니다.

`groovy-calendar-service`는 그중 **개인 일정과 스터디 공식 일정을 통합 관리하는 캘린더 도메인**을
담당하는 서비스입니다.

## 2. 주요 기능

- **일정 통합 조회**: 로그인 유저의 개인 일정 + 소속 스터디의 공식 일정을 함께 조회
- **일정 CRUD**: 개인 일정 추가/상세 조회/수정/삭제
- **일정 등록 가능 스터디 목록 제공**: 일정을 등록할 수 있는(참여 중인) 내 스터디 옵션 조회
- **일정 변경 알림 발행**: 스터디 약속 변경 시 Kafka Outbox로 알림 이벤트 발행

### API 요약

| Method | Path | 설명 |
| :--- | :--- | :--- |
| GET | `/api/calendars` | 개인+스터디 통합 일정 조회 |
| GET | `/api/calendars/studies` | 일정 등록 가능한 내 스터디 목록 |
| POST | `/api/calendars` | 일정 추가 |
| GET | `/api/calendars/{id}` | 일정 상세 조회 |
| PUT | `/api/calendars/{id}` | 일정 수정 |
| DELETE | `/api/calendars/{id}` | 일정 삭제 |

## 3. 시스템 아키텍처 & 데이터베이스

```
브라우저 ─▶ api-gateway :8080 ─▶ calendar-service :8084 ─▶ calendar_db (MySQL)

calendar-service ─┬─ GET /.well-known/jwks.json ──▶ identity-service (JWT 검증)
                   └─ GET /api/studies/{id},
                      /api/users/me/studies, /applications ──▶ study-service (멤버십/스터디 옵션)

calendar-service ── outbox_events → Kafka(notification-events) ─▶ notification-service
```

calendar-service는 identity-service와는 **JWT 검증(JWKS)만** 하고, 이름 조회 등 데이터성 호출은
하지 않습니다 — 캘린더 화면에는 유저 이름이 표시되지 않기 때문입니다. 대신 study-service와는
스터디 멤버십 확인, "내 스터디 옵션" 조회를 위해 자체 로컬 클라이언트(`StudyServiceClient`)로
통신합니다.

### 데이터베이스

- **DB(스키마)명**: `calendar_db`
- **전용 계정**: `calendar_service` (이 스키마에만 `GRANT ALL`, 다른 서비스 DB 접근 권한 없음)

| 테이블 | 역할 | 비고 |
| :--- | :--- | :--- |
| `calendars` | 일정 (`title`, `content`, `date`, `end_date`, `user_id`, `study_id`) | `user_id`는 **identity_db.users.id**, `study_id`는 **study_db.studies.id**(nullable)를 FK 없이 값으로만 참조 |
| `outbox_events` | Transactional Outbox | Kafka `notification-events` 토픽으로 발행되는 큐 |

## 4. 기술 스택

| 카테고리 | 기술 |
| :--- | :--- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Build Tool | Gradle (멀티모듈) |
| Data Access | Spring Data JPA + MySQL |
| DB Migration | Flyway (`V1__baseline_schema.sql`) |
| Security | Spring Security + JWT 검증 전용(`security-common`) |
| 메시징 | Spring Kafka (Transactional Outbox 발행, SASL_PLAINTEXT) |
| 서비스 간 호출 복원력 | Resilience4j CircuitBreaker + Retry (study-service 호출용) |
| Observability | Actuator + Micrometer Tracing(OTLP → Tempo) + Micrometer Prometheus |
| Logging | JSON 구조화 로그 → Loki |
| 기타 | Lombok |

study/content-service와 동일한 5개 공유 라이브러리(`event-contract`, `observability`,
`web-common`, `security-common`, `client-common`)를 의존하지만, `client-common`의
`UserServiceClient`는 실제로 쓰지 않습니다(`ResilientCallExecutor`만 사용) — study-service 호출용
클라이언트는 이 서비스 로컬 코드(`client/StudyServiceClient.java`)로 별도 구현되어 있습니다.

## 5. 다른 MSA 서비스와의 네트워크 호출 관계

| 방향 | 상대 서비스 | 엔드포인트 / 채널 | 용도 |
| :--- | :--- | :--- | :--- |
| 아웃바운드 | identity-service | `GET /.well-known/jwks.json` | JWT 검증용 공개키 |
| 아웃바운드 | study-service | `GET /api/studies/{id}` | 스터디 상세 + 멤버십(방장 여부) 확인 |
| 아웃바운드 | study-service | `GET /api/users/me/studies`, `/applications` | 일정 등록 가능한 "내 스터디 옵션" 조합 |
| 아웃바운드 | Kafka `notification-events` | Outbox 발행 | 스터디 일정 변경 알림 이벤트 |
| 인바운드 | api-gateway | `/api/calendars/**` | 외부 요청 라우팅 |

## 6. 로컬 실행 방법

이 레포는 Gradle 멀티모듈 프로젝트이며 별도 docker-compose 파일은 포함하지 않습니다. MySQL,
Kafka, identity-service, study-service가 먼저 떠 있어야 정상 동작합니다.

```bash
# 1) MySQL에 calendar_db 스키마 + calendar_service 계정 준비
# 2) Kafka, identity-service, study-service 기동

./gradlew :services:calendar-service:bootRun

# 또는 Docker 이미지 빌드 (빌드 컨텍스트 = 이 레포 루트)
docker build -t groovy-calendar-service .
docker run -p 8084:8084 \
  -e SPRING_DEV_DB_URL="jdbc:mysql://host.docker.internal:3306/calendar_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul" \
  -e SPRING_DEV_DB_USERNAME=calendar_service \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -e JWT_JWKS_URL=http://host.docker.internal:8081/.well-known/jwks.json \
  -e STUDY_SERVICE_URL=http://host.docker.internal:8082 \
  groovy-calendar-service
```

기본 포트는 `8084`입니다.

> study-service 없이는 스터디 약속 표시와 "내 스터디 옵션" 목록이 비어 보입니다. 전체 스택은
> 원본 `Groovy` 레포의 `docker-compose.local.yml` 사용을 권장합니다.

## 7. 기존 모노레포에서 뗀 부분

1. **레거시 단일 모놀리스** (`groovy/`, 삭제됨): `domain/calendar`가 다른 모든 도메인과 한
   애플리케이션 안에 있었습니다. 당시 스키마에는 `description`/`start_time`/`end_time` 컬럼이
   있었으나 이후 버전에서 드롭되고 `content` 컬럼이 추가되는 등 여러 차례 변경을 거쳤는데, 이
   서비스의 `V1__baseline_schema.sql`은 그 **최종 형태만** 그대로 옮겨왔습니다.
2. **모듈러 모놀리스 → MSA** (`backend/`, Gradle 멀티모듈 `groovy-backend-msa`):
   `backend/services/calendar-service/`로 추출되며 자체 DB(`calendar_db`)를 갖게 되었고, 스터디
   정보 조회는 study-service 동기 호출로 대체되었습니다.
3. **폴리레포 분리** (지금 이 레포): `backend/services/calendar-service/`와 study-service와
   동일한 5개 공유 라이브러리를 이 독립 저장소로 복사했습니다. `event-contract`는 실제 발행하는
   `NotificationPayload` 서브셋만 포함합니다. 격리 판단 근거는 원본 `Groovy` 레포의
   `docs/transfer/groovy-calendar-service.md`에 기록되어 있습니다.

## 8. 모니터링 스택에서 관측되는 부분

- **Prometheus**: `job_name: calendar-service`가 `calendar-service:8084/actuator/prometheus`를
  15초 주기로 스크래핑합니다. JVM, HTTP 요청 지연, HikariCP 커넥션 풀(`CalendarHikariPool`)
  지표가 서비스 단위로 분리 수집됩니다.
- **Alertmanager**: `HikariCpuPoolPendingDetected`, `BackendMemoryUsageTooHigh`,
  `BackendCpuSpikeDetected`가 `job="calendar-service"` 라벨로 적용됩니다.
- **Tempo**: gateway → calendar-service → study-service로 이어지는 요청 경로의 트레이스를
  확인할 수 있습니다.
- **Grafana**: `springboot-dashboard.json`(JVM), `backend-app-logs-dashboard.json`(Loki 로그)에서
  `application="calendar-service"`로 필터링 가능.
- **Loki + Alloy**: 컨테이너 stdout(JSON 구조화 로그)을 코드 수정 없이 자동 수집.
- **계약 테스트로서의 관측**: CI의 `contract-test` job이 `StudyServiceClientContractTest`(로컬
  스텁으로 study-service 응답 형태 검증)를 실행합니다.
