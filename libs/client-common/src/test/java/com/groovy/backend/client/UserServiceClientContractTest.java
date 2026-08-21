package com.groovy.backend.client;

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
 * study/content-service에 복붙되어 있던 계약 테스트를, 검증 대상 클래스(UserServiceClient)를
 * client-common으로 통합하면서 함께 옮겼다. identity-service(Provider) 쪽 응답 모양은
 * {@code com.groovy.backend.identity.controller.UserController#getNames}가 반환하는
 * {@code ApiResponse.of("SUCCESS", ..., Map<Long,String>)} 그대로다.
 */
class UserServiceClientContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer stubServer;

	@AfterEach
	void tearDown() {
		if (stubServer != null) {
			stubServer.stop(0);
		}
	}

	@Test
	void identity_service의_names_응답_모양을_흉내낸_스텁에서_UserServiceClient로_이름_맵을_읽을_수_있다() throws Exception {
		// identity-service ApiResponse.of("SUCCESS", "유저 이름 조회에 성공했습니다.", Map<Long,String>)와
		// 정확히 같은 모양 — JSON 객체 키는 항상 문자열이므로 Long id도 "1"처럼 문자열로 직렬화된다.
		String responseJson = objectMapper.writeValueAsString(Map.of(
			"status", "SUCCESS",
			"message", "유저 이름 조회에 성공했습니다.",
			"data", Map.of("1", "홍길동", "2", "김철수")
		));
		int port = startStubServer(responseJson);

		UserServiceClient client = new UserServiceClient("http://localhost:" + port, 2000, 3000);

		Map<Long, String> names = client.findNamesByIds(List.of(1L, 2L));

		assertThat(names).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "홍길동", 2L, "김철수"));
	}

	@Test
	void identity_service가_죽어있으면_예외_대신_빈_맵을_반환한다() {
		UserServiceClient client = new UserServiceClient("http://localhost:1", 200, 200);

		Map<Long, String> names = client.findNamesByIds(List.of(1L));

		assertThat(names).isEmpty();
	}

	private int startStubServer(String responseBody) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/api/users/names", exchange -> {
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
