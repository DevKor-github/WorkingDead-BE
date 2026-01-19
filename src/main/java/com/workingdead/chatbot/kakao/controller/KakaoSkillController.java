package com.workingdead.chatbot.kakao.controller;

import com.workingdead.chatbot.kakao.dto.KakaoRequest;
import com.workingdead.chatbot.kakao.dto.KakaoResponse;
import com.workingdead.chatbot.kakao.scheduler.KakaoWendyScheduler;
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
 */
@Tag(name = "Kakao Chatbot", description = "카카오 챗봇 스킬 API")
@RestController
@RequestMapping("/kakao/skill")
@RequiredArgsConstructor
@Slf4j
public class KakaoSkillController {

    private final KakaoWendyService kakaoWendyService;
    private final KakaoWendyScheduler kakaoWendyScheduler;

    /**
     * 메인 스킬 엔드포인트 (폴백 블록)
     * 모든 발화를 여기서 처리
     */
    @Operation(summary = "메인 스킬 (폴백)")
    @PostMapping("/main")
    public ResponseEntity<KakaoResponse> handleMain(@RequestBody KakaoRequest request) {
        String userKey = request.getUserKey();
        String utterance = request.getUtterance();

        log.info("[Kakao Skill] userKey={}, utterance={}", userKey, utterance);

        if (utterance == null || utterance.isBlank()) {
            return ResponseEntity.ok(kakaoWendyService.help());
        }

        String trimmed = utterance.trim();

        // 1. 웬디 시작
        if (trimmed.equals("웬디 시작") || trimmed.equals("시작")) {
            return ResponseEntity.ok(kakaoWendyService.startSession(userKey));
        }

        // 2. 도움말
        if (trimmed.equals("웬디 도움말") || trimmed.equals("도움말") || trimmed.equals("/help")) {
            return ResponseEntity.ok(kakaoWendyService.help());
        }

        // 3. 웬디 종료
        if (trimmed.equals("웬디 종료") || trimmed.equals("종료")) {
            kakaoWendyScheduler.stopSchedule(userKey);
            return ResponseEntity.ok(kakaoWendyService.endSession(userKey));
        }

        // 4. 웬디 결과
        if (trimmed.equals("웬디 결과") || trimmed.equals("결과") || trimmed.equals("결과 확인")) {
            return ResponseEntity.ok(kakaoWendyService.getVoteResult(userKey));
        }

        // 5. 웬디 재투표
        if (trimmed.equals("웬디 재투표") || trimmed.equals("재투표")) {
            return ResponseEntity.ok(kakaoWendyService.revote(userKey));
        }

        // 세션 상태에 따른 처리
        SessionState state = kakaoWendyService.getSessionState(userKey);

        switch (state) {
            case WAITING_PARTICIPANTS:
                // 참석자 이름 입력
                return ResponseEntity.ok(kakaoWendyService.addParticipants(userKey, trimmed));

            case WAITING_WEEKS:
                // 주차 선택
                Integer weeks = kakaoWendyService.parseWeeks(trimmed);
                if (weeks != null) {
                    KakaoResponse response = kakaoWendyService.createVote(userKey, weeks);
                    kakaoWendyScheduler.startSchedule(userKey);
                    return ResponseEntity.ok(response);
                }
                break;

            default:
                break;
        }

        // 알 수 없는 입력
        return ResponseEntity.ok(kakaoWendyService.unknownInput(userKey));
    }

    /**
     * 웬디 시작 스킬 (전용 블록)
     */
    @Operation(summary = "웬디 시작")
    @PostMapping("/start")
    public ResponseEntity<KakaoResponse> handleStart(@RequestBody KakaoRequest request) {
        String userKey = request.getUserKey();
        log.info("[Kakao Skill] START - userKey={}", userKey);
        return ResponseEntity.ok(kakaoWendyService.startSession(userKey));
    }

    /**
     * 웬디 종료 스킬 (전용 블록)
     */
    @Operation(summary = "웬디 종료")
    @PostMapping("/end")
    public ResponseEntity<KakaoResponse> handleEnd(@RequestBody KakaoRequest request) {
        String userKey = request.getUserKey();
        log.info("[Kakao Skill] END - userKey={}", userKey);
        kakaoWendyScheduler.stopSchedule(userKey);
        return ResponseEntity.ok(kakaoWendyService.endSession(userKey));
    }

    /**
     * 주차 선택 스킬 (전용 블록)
     * params에서 weeks 값을 받음
     */
    @Operation(summary = "주차 선택")
    @PostMapping("/select-week")
    public ResponseEntity<KakaoResponse> handleSelectWeek(@RequestBody KakaoRequest request) {
        String userKey = request.getUserKey();
        String weeksParam = request.getParam("weeks");

        log.info("[Kakao Skill] SELECT_WEEK - userKey={}, weeks={}", userKey, weeksParam);

        int weeks = 0;
        if (weeksParam != null) {
            try {
                weeks = Integer.parseInt(weeksParam);
            } catch (NumberFormatException e) {
                Integer parsed = kakaoWendyService.parseWeeks(weeksParam);
                if (parsed != null) weeks = parsed;
            }
        }

        KakaoResponse response = kakaoWendyService.createVote(userKey, weeks);
        kakaoWendyScheduler.startSchedule(userKey);
        return ResponseEntity.ok(response);
    }

    /**
     * 결과 조회 스킬 (전용 블록)
     */
    @Operation(summary = "투표 결과 조회")
    @PostMapping("/result")
    public ResponseEntity<KakaoResponse> handleResult(@RequestBody KakaoRequest request) {
        String userKey = request.getUserKey();
        log.info("[Kakao Skill] RESULT - userKey={}", userKey);
        return ResponseEntity.ok(kakaoWendyService.getVoteResult(userKey));
    }

    /**
     * 재투표 스킬 (전용 블록)
     */
    @Operation(summary = "재투표")
    @PostMapping("/revote")
    public ResponseEntity<KakaoResponse> handleRevote(@RequestBody KakaoRequest request) {
        String userKey = request.getUserKey();
        log.info("[Kakao Skill] REVOTE - userKey={}", userKey);
        kakaoWendyScheduler.stopSchedule(userKey);
        return ResponseEntity.ok(kakaoWendyService.revote(userKey));
    }

    /**
     * 도움말 스킬 (전용 블록)
     */
    @Operation(summary = "도움말")
    @PostMapping("/help")
    public ResponseEntity<KakaoResponse> handleHelp(@RequestBody KakaoRequest request) {
        log.info("[Kakao Skill] HELP - userKey={}", request.getUserKey());
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