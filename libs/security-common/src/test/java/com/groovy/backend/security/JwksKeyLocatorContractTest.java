package com.groovy.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.RsaPublicJwk;

/**
 * study/calendar/content/notification-service 4곳에서 쓰는 JwksKeyLocator에 대한 Consumer 쪽
 * 계약 테스트(원래 notification-service에만 있던 것을 security-common으로 통합하면서 함께
 * 옮겼다). Provider(identity-service) 쪽의 대응 테스트는 identity-service의
 * JwksControllerContractTest 참고.
 *
 * Spring Cloud Contract의 stub-runner 대신, JDK 내장 HttpServer로 identity-service의
 * JwksController와 동일한 응답 모양({"keys":[...]}, jjwt RsaPublicJwk를 LinkedHashMap으로 감싼
 * 형태)을 흉내 낸 스텁을 띄우고, 실제 JwksKeyLocator가 그걸 제대로 소비해 Key를 뽑아내는지
 * 검증한다 — 새 프레임워크 의존성 없이(이미 이 모듈이 갖고 있는 jjwt만으로) 계약을 검증할 수 있다.
 */
class JwksKeyLocatorContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer stubServer;

	@AfterEach
	void tearDown() {
		if (stubServer != null) {
			stubServer.stop(0);
		}
	}

	@Test
	void identity_service의_jwks_응답_모양을_흉내낸_스텁에서_JwksKeyLocator로_실제_서명된_JWT를_검증할_수_있다() throws Exception {
		// Provider(identity-service)의 TokenProvider와 정확히 같은 방식으로 kid를 헤더에 싣고
		// RS256으로 서명한다 — 그래야 jjwt가 locate()에 진짜 ProtectedHeader(파싱된 JWS 헤더)를
		// 넘겨준다. 헤더 객체를 손으로 만들면(Jwts.header().keyId(...).build()) DefaultHeader가
		// 나와 ProtectedHeader가 아니게 되어 이 검증을 흉내 낼 수 없다 — 그래서 진짜 서명/검증
		// 왕복으로 테스트한다.
		KeyPair keyPair = Jwts.SIG.RS256.keyPair().build();
		RsaPublicJwk publicJwk = Jwks.builder()
			.key((RSAPublicKey) keyPair.getPublic())
			.idFromThumbprint()
			.build();

		String jwksJson = toProviderShapedJwksJson(publicJwk);
		int port = startStubServer(jwksJson);

		JwksKeyLocator locator = new JwksKeyLocator(
			"http://localhost:" + port + "/.well-known/jwks.json", 2000, 3000, 300);

		String token = Jwts.builder()
			.header().keyId(publicJwk.getId()).and()
			.subject("tracer-contract-test")
			.signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
			.compact();

		Jws<Claims> parsed = Jwts.parser().keyLocator(locator).build().parseSignedClaims(token);

		assertThat(parsed.getPayload().getSubject()).isEqualTo("tracer-contract-test");
	}

	// identity-service JwksController.toPlainMap()과 정확히 같은 변환 — 계약이 이 모양이라는 걸 명시한다.
	private String toProviderShapedJwksJson(Jwk<?> jwk) throws Exception {
		List<Map<String, Object>> keys = List.of(new LinkedHashMap<>(jwk));
		return objectMapper.writeValueAsString(Map.of("keys", keys));
	}

	private int startStubServer(String responseBody) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/.well-known/jwks.json", exchange -> {
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
