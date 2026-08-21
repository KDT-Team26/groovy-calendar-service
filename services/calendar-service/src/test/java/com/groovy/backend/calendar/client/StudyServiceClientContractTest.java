package com.groovy.backend.calendar.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * MSA 전환(calendar-service 추출): groovy(레거시)/calendar-service가 study-service의
 * "GET /api/studies/{id}/members"(승인된 멤버 목록, 게이트웨이를 거치지 않는 서비스 간 내부 API)를
 * 정확히 소비하는지 검증하는 Consumer 쪽 계약 테스트. Provider 쪽 응답 모양은
 * {@code com.groovy.backend.study.controller.StudyController#getApprovedMemberIds}가 반환하는
 * {@code ApiResponse.of("SUCCESS", ..., List<Long>)} 그대로다.
 *
 * study-service의 UserServiceClientContractTest와 동일한 방식(JDK 내장 HttpServer 스텁)으로,
 * 새 프레임워크 의존성 없이 검증한다.
 */
class StudyServiceClientContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer stubServer;

	@AfterEach
	void tearDown() {
		if (stubServer != null) {
			stubServer.stop(0);
		}
	}

	@Test
	void study_service의_members_응답_모양을_흉내낸_스텁에서_승인된_멤버_id_목록을_읽을_수_있다() throws Exception {
		String responseJson = objectMapper.writeValueAsString(Map.of(
			"status", "SUCCESS",
			"message", "승인된 멤버 목록 조회에 성공했습니다.",
			"data", List.of(1, 2, 3)
		));
		int port = startStubServer("/api/studies/10/members", responseJson);

		StudyServiceClient client = new StudyServiceClient("http://localhost:" + port, 2000, 3000);

		List<Long> memberIds = client.getApprovedMemberUserIds(10L);

		assertThat(memberIds).containsExactlyInAnyOrder(1L, 2L, 3L);
	}

	@Test
	void study_service가_죽어있으면_예외_대신_빈_목록을_반환한다() {
		StudyServiceClient client = new StudyServiceClient("http://localhost:1", 200, 200);

		List<Long> memberIds = client.getApprovedMemberUserIds(10L);

		assertThat(memberIds).isEmpty();
	}

	private int startStubServer(String path, String responseBody) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext(path, exchange -> {
			byte[] bytes = responseBody.getBytes();
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		this.stubServer = server;
		TimeUnit.MILLISECONDS.sleep(50);
		return server.getAddress().getPort();
	}
}
