package com.groovy.backend.security;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * study/calendar/content/notification-service 4곳(= identity-service가 발급한 JWT를 검증만
 * 하는 서비스)에 복붙되어 있던 클래스를 security-common으로 통합했다(SecurityCommonAutoConfiguration이
 * 빈으로 등록). JwksKeyLocator가 identity-service의 공개키로 서명을 검증만 한다. principal을
 * email이 아니라 userId(Long)로 바로 채운다 — 이 4개 서비스에는 User 테이블이 없어서 email로는
 * 아무것도 조회할 수 없고, uid 클레임이 이미 신원 그 자체이기 때문이다.
 *
 * identity-service는 발급자(개인키로 서명, email 기반 principal)라 이 클래스를 쓰지 않고 자기
 * 로컬 TokenProvider를 그대로 유지한다.
 */
@Slf4j
@RequiredArgsConstructor
public class TokenProvider {

	private static final String USER_ID_CLAIM_KEY = "uid";
	private static final String ROLE_CLAIM_KEY = "role";

	private final JwksKeyLocator jwksKeyLocator;

	public boolean validateToken(String token) {
		try {
			Jwts.parser().keyLocator(jwksKeyLocator).build().parseSignedClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			log.warn("토큰 검증 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
			return false;
		}
	}

	public Authentication getAuthentication(String token) {
		Claims claims = parseClaims(token);
		Long userId = claims.get(USER_ID_CLAIM_KEY, Long.class);
		String role = claims.get(ROLE_CLAIM_KEY, String.class);

		List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

		return new UsernamePasswordAuthenticationToken(userId, null, authorities);
	}

	private Claims parseClaims(String token) {
		return Jwts.parser().keyLocator(jwksKeyLocator).build().parseSignedClaims(token).getPayload();
	}
}
