# Security Configuration 분석

## 개요

이 문서는 WorkingDead 백엔드 프로젝트의 보안 설정을 분석한 문서입니다.

---

## 파일 구조

```
src/main/java/com/workingdead/config/
├── SecurityConfig.java    # Spring Security 설정
└── CorsConfig.java        # CORS 설정
```

---

## 1. SecurityConfig.java

### 위치
`src/main/java/com/workingdead/config/SecurityConfig.java`

### 역할
Spring Security의 핵심 보안 설정을 담당합니다.

### 주요 설정

| 설정 항목 | 값 | 설명 |
|-----------|-----|------|
| CORS | 활성화 | CorsConfig에서 정의한 설정 사용 |
| CSRF | 비활성화 | REST API 서버이므로 CSRF 토큰 불필요 |
| Form Login | 비활성화 | 기본 로그인 폼 사용 안함 |
| HTTP Basic | 비활성화 | 브라우저 팝업 로그인 사용 안함 |

### 접근 권한 설정

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    ).permitAll()
    .anyRequest().permitAll()
);
```

**현재 상태**: 모든 요청에 대해 `permitAll()` 설정
- Swagger 문서 경로: `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
- 기타 모든 요청: 인증 없이 접근 가능

### 보안 수준
현재는 **인증이 구현되어 있지 않은 상태**입니다. 모든 API 엔드포인트가 공개되어 있습니다.

---

## 2. CorsConfig.java

### 위치
`src/main/java/com/workingdead/config/CorsConfig.java`

### 역할
Cross-Origin Resource Sharing (CORS) 정책을 정의합니다.

### 허용된 Origin 목록

| Origin | 환경 |
|--------|------|
| `http://localhost:3000` | 로컬 개발 (React 기본 포트) |
| `http://localhost:8081` | 로컬 개발 |
| `http://localhost:8080` | 로컬 개발 |
| `http://10.0.2.2:8080` | Android 에뮬레이터 |
| `http://localhost:5173` | 로컬 개발 (Vite 기본 포트) |
| `http://whend.app` | 프로덕션 (HTTP) |
| `https://whend.app` | 프로덕션 (HTTPS) |
| `https://whendy.netlify.app` | Netlify 배포 |

### CORS 상세 설정

| 설정 | 값 | 설명 |
|------|-----|------|
| Allowed Methods | GET, POST, PUT, PATCH, DELETE, OPTIONS | 모든 REST 메서드 허용 |
| Allowed Headers | `*` | 모든 헤더 허용 |
| Exposed Headers | Authorization, Content-Type | 클라이언트에서 접근 가능한 응답 헤더 |
| Allow Credentials | `true` | 쿠키/인증 정보 포함 허용 |
| Max Age | 3600초 (1시간) | Preflight 요청 캐시 시간 |

---

## 현재 인증 상태 요약

```
┌─────────────────────────────────────────────────────┐
│                    현재 상태                         │
├─────────────────────────────────────────────────────┤
│  - JWT 인증: 미구현                                  │
│  - OAuth 로그인: 미구현                              │
│  - Session 기반 인증: 미구현                         │
│  - 모든 API: 공개 접근 가능 (permitAll)              │
└─────────────────────────────────────────────────────┘
```

---

## 향후 보안 강화 시 고려사항

1. **JWT 토큰 인증 도입**
   - Access Token / Refresh Token 구조
   - JwtAuthenticationFilter 추가

2. **OAuth2 소셜 로그인**
   - Google, Kakao, Naver 등 연동
   - OAuth2LoginSuccessHandler 구현

3. **엔드포인트별 권한 분리**
   ```java
   .requestMatchers("/api/admin/**").hasRole("ADMIN")
   .requestMatchers("/api/user/**").authenticated()
   .anyRequest().permitAll()
   ```

4. **Rate Limiting**
   - API 호출 횟수 제한으로 DDoS 방어

---

## 관련 의존성

`build.gradle`에 Spring Security가 포함되어 있어야 합니다:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
```

---

*문서 생성일: 2026-01-19*