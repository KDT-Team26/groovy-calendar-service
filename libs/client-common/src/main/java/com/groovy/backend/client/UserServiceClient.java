package com.groovy.backend.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.groovy.backend.common.response.ApiResponse;

/**
 * study/content-service에 복붙되어 있던 클래스를 client-common으로 통합했다
 * (ClientCommonAutoConfiguration이 빈으로 등록). 작성자/신청자 이름처럼 표시용 이름이 필요한데
 * 자기 서비스엔 User 테이블이 없어서 identity-service에 물어봐야 하는 서비스들이 쓴다.
 *
 * identity-service가 죽어 있거나 느려지면(ResilientCallExecutor가 서킷브레이커/재시도로 처리)
 * 예외 대신 빈 Map을 반환한다 — 이름 없이("null")라도 나머지 데이터는 보여주는 편이 요청 전체가
 * 500으로 죽는 것보다 낫다는 기존 판단을 그대로 유지한다.
 */
public class UserServiceClient {

	private final RestClient restClient;
	private final ResilientCallExecutor executor;

	public UserServiceClient(String identityServiceUrl, long connectTimeoutMs, long readTimeoutMs) {
		var httpClient = java.net.http.HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(connectTimeoutMs))
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

		this.restClient = RestClient.builder()
			.baseUrl(identityServiceUrl)
			.requestFactory(requestFactory)
			.build();
		this.executor = new ResilientCallExecutor("user-service-client");
	}

	public Map<Long, String> findNamesByIds(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}

		return executor.execute(
			() -> {
				ApiResponse<Map<Long, String>> response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/api/users/names").queryParam("ids", userIds).build())
					.retrieve()
					.body(new ParameterizedTypeReference<ApiResponse<Map<Long, String>>>() {
					});
				return response == null || response.data() == null ? Map.<Long, String>of() : response.data();
			},
			Map::of);
	}
}
