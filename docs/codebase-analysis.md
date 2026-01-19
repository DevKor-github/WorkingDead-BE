# WorkingDead Backend - Codebase 분석

## 프로젝트 개요

**When:D (웬디)** - 디스코드 기반 일정 조율 서비스

팀/그룹의 회식, 모임 일정을 조율하기 위한 투표 시스템을 제공하며, 디스코드 봇을 통해 자동화된 투표 생성 및 알림 기능을 제공합니다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.5.7 |
| **Database** | PostgreSQL (AWS RDS) |
| **ORM** | Spring Data JPA + Hibernate |
| **Security** | Spring Security |
| **Session** | Spring Session JDBC |
| **Cache** | Redis |
| **API Docs** | SpringDoc OpenAPI (Swagger) |
| **Discord Bot** | JDA 5.0.0-beta.24 |
| **Build Tool** | Gradle |

---

## 프로젝트 구조

```
src/main/java/com/workingdead/
├── WorkingdeadApplication.java          # 메인 애플리케이션
│
├── config/                              # 설정 클래스
│   ├── SecurityConfig.java              # Spring Security 설정
│   ├── CorsConfig.java                  # CORS 설정
│   ├── OpenApiConfig.java               # Swagger 설정
│   └── DiscordBotConfig.java            # Discord JDA 설정
│
├── enum/
│   └── Period.java                      # 시간대 enum (LUNCH/DINNER)
│
├── meet/                                # 핵심 도메인 (투표 시스템)
│   ├── controller/
│   │   ├── VoteController.java          # 투표 CRUD API
│   │   ├── ParticipantController.java   # 참여자 관리 API
│   │   ├── VoteResultController.java    # 투표 결과 조회 API
│   │   └── VoteDateRangeController.java # 날짜 범위 조회 API
│   │
│   ├── service/
│   │   ├── VoteService.java             # 투표 비즈니스 로직
│   │   ├── ParticipantService.java      # 참여자 비즈니스 로직
│   │   ├── VoteResultService.java       # 결과 집계 로직
│   │   ├── VoteDateRangeService.java    # 날짜 범위 로직
│   │   └── PriorityService.java         # 우선순위 로직
│   │
│   ├── entity/
│   │   ├── Vote.java                    # 투표 엔티티
│   │   ├── Participant.java             # 참여자 엔티티
│   │   ├── ParticipantSelection.java    # 일정 선택 엔티티
│   │   └── PriorityPreference.java      # 우선순위 엔티티
│   │
│   ├── repository/
│   │   ├── VoteRepository.java
│   │   ├── ParticipantRepository.java
│   │   ├── ParticipantSelectionRepository.java
│   │   └── PriorityPreferenceRepository.java
│   │
│   ├── dto/
│   │   ├── VoteDtos.java
│   │   ├── ParticipantDtos.java
│   │   ├── VoteResultDtos.java
│   │   ├── VoteDateRangeDtos.java
│   │   └── PriorityDtos.java
│   │
│   └── application/
│       └── VoteApplicationService.java  # 애플리케이션 서비스
│
└── chatbot/                             # Discord 봇 모듈
    ├── command/
    │   └── WendyCommand.java            # 디스코드 명령어 핸들러
    │
    ├── service/
    │   ├── WendyService.java            # 봇 서비스 인터페이스
    │   ├── WendyServiceImpl.java        # 봇 서비스 구현체
    │   └── WendyNotifier.java           # 알림 서비스
    │
    ├── scheduler/
    │   └── WendyScheduler.java          # 스케줄러 (리마인드)
    │
    └── dto/
        └── VoteResult.java
```

---

## 핵심 도메인 모델

### ERD (Entity Relationship)

```
┌─────────────────┐
│      Vote       │
├─────────────────┤
│ id (PK)         │
│ name            │
│ code (unique)   │
│ startDate       │
│ endDate         │
│ createdAt       │
└────────┬────────┘
         │ 1:N
         ▼
┌─────────────────┐
│   Participant   │
├─────────────────┤
│ id (PK)         │
│ vote_id (FK)    │
│ displayName     │
│ submitted       │
│ submittedAt     │
└────────┬────────┘
         │ 1:N                    1:N
         ├──────────────────────────┐
         ▼                          ▼
┌─────────────────────┐   ┌─────────────────────┐
│ ParticipantSelection│   │  PriorityPreference │
├─────────────────────┤   ├─────────────────────┤
│ id (PK)             │   │ id (PK)             │
│ participant_id (FK) │   │ participant_id (FK) │
│ vote_id (FK)        │   │ vote_id (FK)        │
│ date                │   │ date                │
│ period (LUNCH/DINNER│   │ period              │
│ selected            │   │ priorityIndex (1~3) │
└─────────────────────┘   │ weight              │
                          │ createdAt           │
                          └─────────────────────┘
```

### 엔티티 설명

| 엔티티 | 설명 |
|--------|------|
| **Vote** | 투표 세션. 고유 `code`로 공유 링크 생성 |
| **Participant** | 투표 참여자. Vote에 종속 |
| **ParticipantSelection** | 참여자의 날짜/시간대 선택 (true/false) |
| **PriorityPreference** | 참여자의 우선순위 (1순위, 2순위, 3순위) |

---

## API 엔드포인트

### Vote API (`/votes`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/votes` | 전체 투표 목록 조회 |
| GET | `/votes/{id}` | 투표 상세 조회 |
| GET | `/votes/share/{code}` | 공유 코드로 투표 조회 |
| POST | `/votes` | 새 투표 생성 |
| PATCH | `/votes/{id}` | 투표 정보 수정 |
| DELETE | `/votes/{id}` | 투표 삭제 |
| GET | `/votes/{voteId}/dateRange` | 날짜 범위 조회 |
| GET | `/votes/{voteId}/result` | 투표 결과 조회 |

### Participant API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/votes/{voteId}/participants` | 참여자 목록 조회 |
| POST | `/votes/{voteId}/participants` | 참여자 추가 |
| PATCH | `/participants/{id}` | 참여자 정보 수정 |
| DELETE | `/participants/{id}` | 참여자 삭제 |
| GET | `/participants/{id}/choices` | 참여자 선택 정보 조회 |
| PATCH | `/participants/{id}/schedule` | 일정 제출 |
| POST | `/participants/{id}` | 우선순위 설정 |

---

## 투표 결과 집계 로직

### 정렬 기준 (VoteResultService)

```
1순위: 투표 인원수 (많을수록 상위)
2순위: 우선순위 Index 합계 (작을수록 상위)
3순위: 날짜 (빠를수록 상위)
```

### 예시

| 날짜 | 시간대 | 인원 | 우선순위합 | 순위 |
|------|--------|------|------------|------|
| 01/20 | LUNCH | 5명 | 3 | 1위 |
| 01/21 | DINNER | 5명 | 7 | 2위 |
| 01/19 | LUNCH | 4명 | 2 | 3위 |

---

## Discord Bot (웬디)

### 아키텍처

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  WendyCommand   │────▶│  WendyService   │────▶│   VoteService   │
│  (ListenerAdapter│     │  Impl           │     │   Participant   │
│   이벤트 핸들러) │     │  (세션 관리)    │     │   Service       │
└─────────────────┘     └─────────────────┘     └─────────────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│  WendyScheduler │────▶│  WendyNotifier  │
│  (시간 기반     │     │  (메시지 전송)  │
│   태스크 관리)  │     └─────────────────┘
└─────────────────┘
```

### 명령어

| 명령어 | 설명 |
|--------|------|
| `웬디 시작` | 일정 조율 세션 시작 |
| `웬디 종료` | 세션 종료 |
| `웬디 재투표` | 동일 참석자로 새 투표 생성 |
| `웬디 도움말` / `/help` | 도움말 표시 |

### 알림 스케줄

| 시간 | 알림 내용 |
|------|----------|
| 10분 후 | 투표 현황 공유 |
| 15분 후 | 미투표자 독촉 (1차) |
| 1시간 후 | 미투표자 독촉 (2차) |
| 6시간 후 | 미투표자 독촉 (3차) |
| 12시간 후 | 미투표자 독촉 (4차) |
| 24시간 후 | 최후통첩 (1순위로 확정 안내) |

### 세션 관리 (WendyServiceImpl)

```java
// 채널별 상태 관리
private final Set<String> activeSessions;           // 활성 세션
private final Map<String, Map<String, String>> participants;  // 참석자
private final Map<String, Long> channelVoteId;      // 채널 -> 투표ID
private final Map<String, String> channelShareUrl;  // 채널 -> 공유URL
```

---

## 설정 파일

### application.yaml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://[RDS_HOST]:5432/workingdead
    username: postgres
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  session:
    store-type: jdbc

server:
  port: 8080

discord:
  token: ${DISCORD_TOKEN}
```

### 환경 변수

| 변수명 | 설명 |
|--------|------|
| `DB_PASSWORD` | PostgreSQL 비밀번호 |
| `DISCORD_TOKEN` | Discord Bot 토큰 |
| `AWS_ACCESS_KEY_ID` | AWS 액세스 키 |
| `AWS_SECRET_ACCESS_KEY` | AWS 시크릿 키 |

---

## 보안 설정

### 현재 상태

```java
// SecurityConfig.java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
    .anyRequest().permitAll()  // 모든 요청 허용
);
```

- **인증**: 미구현 (모든 API 공개)
- **CSRF**: 비활성화
- **CORS**: 지정된 도메인만 허용

### 허용 도메인 (CorsConfig)

- localhost:3000, 5173, 8080, 8081
- whend.app (HTTP/HTTPS)
- whendy.netlify.app

---

## 핵심 비즈니스 플로우

### 1. 투표 생성 플로우

```
1. 디스코드에서 "웬디 시작" 입력
2. 참석자 선택 (드롭다운 메뉴)
3. 주차 선택 (이번 주 ~ 6주 뒤)
4. Vote 엔티티 생성 + Participant 일괄 생성
5. 공유 URL 반환 (whendy.netlify.app/v/{code})
6. 스케줄러 시작 (리마인드 알림)
```

### 2. 투표 참여 플로우

```
1. 공유 URL 접속
2. 참여자 칩 선택 (본인 선택)
3. 날짜/시간대 선택 (LUNCH/DINNER)
4. 우선순위 설정 (1~3순위, 선택사항)
5. 제출 → ParticipantSelection, PriorityPreference 저장
```

### 3. 결과 조회 플로우

```
1. GET /votes/{voteId}/result
2. 선택된 일정 집계 (selected=true)
3. 우선순위 가중치 계산
4. 정렬: 인원 > 우선순위합 > 날짜
5. 상위 3개 랭킹 반환
```

---

## 의존성 목록 (build.gradle)

```gradle
// Core
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-validation'

// Database
runtimeOnly 'org.postgresql:postgresql'
runtimeOnly 'com.h2database:h2'
implementation 'org.flywaydb:flyway-core'

// Session & Cache
implementation 'org.springframework.session:spring-session-jdbc'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'

// Discord Bot
implementation 'net.dv8tion:JDA:5.0.0-beta.24'

// API Docs
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8'

// Utilities
compileOnly 'org.projectlombok:lombok'
```

---

## 프로젝트 특징

1. **Discord 연동**: JDA 라이브러리로 디스코드 봇 구현
2. **실시간 알림**: 스케줄러 기반 자동 리마인드
3. **우선순위 시스템**: 단순 투표가 아닌 가중치 기반 결과 도출
4. **공유 URL**: 8자리 고유 코드로 간편한 공유
5. **세션 관리**: 채널별 독립적인 세션 상태 관리

---

*문서 생성일: 2026-01-19*