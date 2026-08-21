package com.groovy.backend.calendar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * MSA 전환(calendar-service 추출): groovy(레거시)/study-service의 SchedulingConfig와 동일한
 * 이유(그쪽 주석 참고). OutboxRelay의 @Scheduled 폴링을 켜고, OutboxEventWriter가 쓰는 구
 * Jackson(com.fasterxml.jackson) ObjectMapper를 직접 등록한다(Boot 4 오토컨피그는 tools.jackson
 * 쪽만 만들어준다). EventEnvelope의 Instant occurredAt을 다루므로 JavaTimeModule을 함께
 * 등록해야 한다(직접 만든 ObjectMapper는 클래스패스에 jsr310 모듈이 있어도 자동으로 등록되지
 * 않는다).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper().registerModule(new JavaTimeModule());
	}
}
