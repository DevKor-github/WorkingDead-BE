package com.workingdead.chatbot.kakao.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workingdead.chatbot.kakao.dto.KakaoRequest;
import com.workingdead.chatbot.kakao.dto.KakaoResponse;
import com.workingdead.chatbot.kakao.service.KakaoWendyService;
import com.workingdead.chatbot.kakao.service.KakaoWendyService.SessionState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 카카오 i 오픈빌더 스킬 서버 컨트롤러
 *
 * 카카오톡 챗봇에서 발화를 받아 처리하고 응답을 반환합니다.
 * - 그룹챗: botGroupKey 기반 세션
 */
@Tag(name = "Kakao Chatbot", description = "카카오 챗봇 스킬 API")
@RestController
@RequestMapping("/kakao/skill")
@RequiredArgsConstructor
@Slf4j
public class KakaoSkillController {

    private final KakaoWendyService kakaoWendyService;
    private final ObjectMapper objectMapper;


    /**
     * 메인 스킬 엔드포인트 (폴백 블록)
     * 모든 발화를 여기서 처리
     */
    @Operation(summary = "메인 스킬 (폴백)")
    @PostMapping("/main")
    public ResponseEntity<KakaoResponse> handleMain(@RequestBody KakaoRequest request) {
        String botGroupKey = request.getBotGroupKey();
        String botUserKey = request.getBotUserKey();
        String utterance = request.getUtterance();

        try {
            log.info("[Kakao Skill Raw Request] {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request));
        } catch (Exception e) {
            log.warn("[Kakao Skill] Failed to log raw request: {}", e.getMessage());
        }

        if (botGroupKey == null || botGroupKey.isBlank()) {
            return ResponseEntity.ok(KakaoResponse.simpleText(
                "이 기능은 그룹 채팅방에서만 사용할 수 있어요. 그룹방에서 @웬디를 불러주세요!"
            ));
        }

        if (utterance == null || utterance.isBlank()) {
            return ResponseEntity.ok(kakaoWendyService.help());
        }

        String trimmed = utterance.trim();

        // 1. 웬디 시작
        if (trimmed.equals("웬디 시작") || trimmed.equals("시작")) {
            return ResponseEntity.ok(kakaoWendyService.startSession(botGroupKey));
        }

        // 2. 도움말
        if (trimmed.equals("웬디 도움말") || trimmed.equals("도움말") || trimmed.equals("/help")) {
            return ResponseEntity.ok(kakaoWendyService.help());
        }

        // 3. 웬디 종료
        if (trimmed.equals("웬디 종료") || trimmed.equals("종료")) {
            return ResponseEntity.ok(kakaoWendyService.endSession(botGroupKey));
        }

        // 4. 웬디 결과
        if (trimmed.equals("웬디 결과") || trimmed.equals("결과") || trimmed.equals("결과 확인")) {
            return ResponseEntity.ok(kakaoWendyService.getVoteResult(botGroupKey));
        }

        // 5. 웬디 재투표
        if (trimmed.equals("웬디 재투표") || trimmed.equals("재투표")) {
            return ResponseEntity.ok(kakaoWendyService.revote(botGroupKey));
        }

        // 6. 웬디 {기간} (예: "웬디 2주 후", "웬디 이번주")
        // 기간 입력을 받으면 서버에서 날짜 범위를 계산해 투표를 생성하고 /open-vote 링크를 제공합니다.
        if (trimmed.startsWith("웬디 ")) {
            String arg = trimmed.substring("웬디 ".length()).trim();
            // 예약어는 위에서 이미 처리했지만, 안전하게 한 번 더 방어
            if (!arg.isBlank()
                    && !arg.equals("시작")
                    && !arg.equals("도움말")
                    && !arg.equals("종료")
                    && !arg.equals("결과")
                    && !arg.equals("재투표")
                    && !arg.equals("독촉")) {
                Integer weeks = kakaoWendyService.parseWeeks(arg);
                if (weeks != null) {
                    KakaoResponse response = kakaoWendyService.createVote(botGroupKey, weeks);
                    return ResponseEntity.ok(response);
                }
            }
        }

        // 세션 상태에 따른 처리
        SessionState state = kakaoWendyService.getSessionState(botGroupKey);

        switch (state) {
            case IDLE:
                return ResponseEntity.ok(KakaoResponse.simpleText(
                        "\"웬디 시작\"을 입력해 투표를 시작해 주세요!"
                ));
            case WAITING_WEEKS:
                // 주차 선택
                return ResponseEntity.ok(KakaoResponse.simpleText(
                        "기간을 선택해 주세요! 예) 이번주, 다음주, 2주 후"
                ));
            case VOTE_CREATED:
                return ResponseEntity.ok(KakaoResponse.simpleText(
                        "이미 진행 중인 투표가 있어요.\n" +
                        "- 결과: \"웬디 결과\"\n" +
                        "- 새로 시작: \"웬디 재투표\"\n\n" +
                        "투표하러 가기: /open-vote"
                ));
            default:
                break;
        }

        // 알 수 없는 입력
        return ResponseEntity.ok(kakaoWendyService.unknownInput(botGroupKey));
    }

    /**
     * 웬디 시작 스킬 (전용 블록)
     */
    @Operation(summary = "웬디 시작")
    @PostMapping("/start")
    public ResponseEntity<KakaoResponse> handleStart(@RequestBody KakaoRequest request) {
        String botGroupKey = request.getBotGroupKey();
        log.info("botGroupKey={}", request.getBotGroupKey());
        if (botGroupKey == null || botGroupKey.isBlank()) {
            return ResponseEntity.ok(KakaoResponse.simpleText(
                "이 기능은 그룹 채팅방에서만 사용할 수 있어요. 그룹방에서 @웬디를 불러주세요!"
            ));
        }
        return ResponseEntity.ok(kakaoWendyService.startSession(botGroupKey));
    }

    /**
     * 참석자 등록 스킬 (전용 블록)
     * PRD 기준: 발화에서 멘션된 유저 식별 결과로 botUserKey 목록을 params로 전달받는 형태를 우선 지원
     * - 지원 params 키 예시: botUserKeys / participants / mentionedUserKeys
     */
    @Operation(summary = "참석자 등록")
    @PostMapping("/participants")
    public ResponseEntity<KakaoResponse> handleParticipants(@RequestBody KakaoRequest request) {
        String botGroupKey = request.getBotGroupKey();
        if (botGroupKey == null || botGroupKey.isBlank()) {
            return ResponseEntity.ok(KakaoResponse.simpleText(
                "이 기능은 그룹 채팅방에서만 사용할 수 있어요. 그룹방에서 @웬디를 불러주세요!"
            ));
        }
        return ResponseEntity.ok(KakaoResponse.simpleText(
                "참석자 단계는 생략했어요. 기간을 입력해 주세요! 예) 이번주, 다음주, 2주 후"
        ));
    }

    /**
     * 웬디 종료 스킬 (전용 블록)
     */
    @Operation(summary = "웬디 종료")
    @PostMapping("/end")
    public ResponseEntity<KakaoResponse> handleEnd(@RequestBody KakaoRequest request) {
        String botGroupKey = request.getBotGroupKey();
        if (botGroupKey == null || botGroupKey.isBlank()) {
            return ResponseEntity.ok(KakaoResponse.simpleText(
                "이 기능은 그룹 채팅방에서만 사용할 수 있어요. 그룹방에서 @웬디를 불러주세요!"
            ));
        }
        return ResponseEntity.ok(kakaoWendyService.endSession(botGroupKey));
    }

    /**
     * 주차 선택 스킬 (전용 블록)
     * params에서 weeks 값을 받음
     */
    @Operation(summary = "주차 선택")
    @PostMapping("/select-week")
    public ResponseEntity<KakaoResponse> handleSelectWeek(@RequestBody KakaoRequest request) {
        String botGroupKey = request.getBotGroupKey();
        String weeksParam = request.getParam("weeks");
        log.info("[SKILL] select-week called botGroupKey={}", botGroupKey);

        if (botGroupKey == null || botGroupKey.isBlank()) {
            return ResponseEntity.ok(KakaoResponse.simpleText(
                "이 기능은 그룹 채팅방에서만 사용할 수 있어요. 그룹방에서 @웬디를 불러주세요!"
            ));
        }

        // 상태 검증: 주차 선택 단계에서만 허용
        SessionState state = kakaoWendyService.getSessionState(botGroupKey);
        if (state != SessionState.WAITING_WEEKS) {
            return ResponseEntity.ok(kakaoWendyService.unknownInput(botGroupKey));
        }

        // weeks 파싱 (param 우선, 없으면 utterance로 보조)
        String candidate = (weeksParam != null && !weeksParam.isBlank()) ? weeksParam : request.getUtterance();
        Integer weeks = (candidate == null) ? null : kakaoWendyService.parseWeeks(candidate.trim());

        if (weeks == null || weeks < 0) {
            return ResponseEntity.ok(KakaoResponse.simpleText("주차 선택 값을 확인하지 못했어요. 다시 선택해 주세요."));
        }

        KakaoResponse response = kakaoWendyService.createVote(botGroupKey, weeks);
        return ResponseEntity.ok(response);
    }

    /**
     * 결과 조회 스킬 (전용 블록)
     */
    @Operation(summary = "투표 결과 조회")
    @PostMapping("/result")
    public ResponseEntity<KakaoResponse> handleResult(@RequestBody KakaoRequest request) {
        String botGroupKey = request.getBotGroupKey();
        if (botGroupKey == null || botGroupKey.isBlank()) {
            return ResponseEntity.ok(KakaoResponse.simpleText(
                "이 기능은 그룹 채팅방에서만 사용할 수 있어요. 그룹방에서 @웬디를 불러주세요!"
            ));
        }
        return ResponseEntity.ok(kakaoWendyService.getVoteResult(botGroupKey));
    }

    /**
     * 재투표 스킬 (전용 블록)
     */
    @Operation(summary = "재투표")
    @PostMapping("/revote")
    public ResponseEntity<KakaoResponse> handleRevote(@RequestBody KakaoRequest request) {
        String botGroupKey = request.getBotGroupKey();
        if (botGroupKey == null || botGroupKey.isBlank()) {
            return ResponseEntity.ok(KakaoResponse.simpleText(
                "이 기능은 그룹 채팅방에서만 사용할 수 있어요. 그룹방에서 @웬디를 불러주세요!"
            ));
        }
        return ResponseEntity.ok(kakaoWendyService.revote(botGroupKey));
    }

    /**
     * 도움말 스킬 (전용 블록)
     */
    @Operation(summary = "도움말")
    @PostMapping("/help")
    public ResponseEntity<KakaoResponse> handleHelp(@RequestBody KakaoRequest request) {
        return ResponseEntity.ok(kakaoWendyService.help());
    }

    /**
     * 헬스체크 (카카오 스킬 서버 상태 확인용)
     */
    @Operation(summary = "헬스체크")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}