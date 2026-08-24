-- IR-312: OutboxRelay가 @Scheduled 배치로 발행할 때 원래 요청의 trace context가 이미 사라진
-- 상태라, 접수 시점(OutboxEventWriter.write())의 W3C traceparent를 같은 트랜잭션으로 함께
-- 저장해뒀다가 릴레이가 그대로 Kafka header에 옮겨 쓴다.
ALTER TABLE `outbox_events`
  ADD COLUMN `trace_parent` varchar(55) DEFAULT NULL AFTER `payload`;
