package com.workingdead.chatbot.kakao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workingdead.config.KakaoConfig;
import com.workingdead.meet.dto.ParticipantDtos.ParticipantStatusRes;
import com.workingdead.meet.dto.VoteResultDtos.RankingRes;
import com.workingdead.meet.dto.VoteResultDtos.VoteResultRes;
import com.workingdead.meet.service.ParticipantService;
import com.workingdead.meet.service.VoteResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 카카오톡 알림 서비스
 *
 * 카카오 비즈메시지 API를 통해 사용자에게 알림을 전송합니다.
 * - 친구톡: 카카오톡 채널 친구에게 메시지 전송
 * - 알림톡: 템플릿 기반 알림 메시지 전송 (사전 승인 필요)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoNotifier {

    private final KakaoConfig kakaoConfig;
    private final KakaoWendyService kakaoWendyService;
    private final VoteResultService voteResultService;
    private final ParticipantService participantService;
    private final RestTemplate kakaoRestTemplate;
    private final ObjectMapper objectMapper;

    // 카카오 API 엔드포인트
    private static final String KAKAO_SEND_ME_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send";
    private static final String KAKAO_FRIEND_MESSAGE_URL = "https://kapi.kakao.com/v1/api/talk/friends/message/default/send";

    /**
     * 투표 현황 공유 (저장된 세션 정보 기반)
     */
    public void shareVoteStatus(String userKey) {
        try {
            // KakaoWendyService에서 voteId 조회 (리플렉션 또는 public 메서드 필요)
            // 현재는 로그만 출력
            log.info("[Kakao Notifier] Vote status share requested for userKey: {}", userKey);

            // TODO: 실제 카카오 메시지 API 호출
            // 카카오 비즈메시지를 사용하려면 비즈니스 채널 등록 및 발신 프로필 설정 필요

        } catch (Exception e) {
            log.error("[Kakao Notifier] Failed to share vote status: {}", e.getMessage());
        }
    }

    /**
     * 미투표자 리마인드
     */
    public void remindNonVoters(String userKey, RemindTiming timing) {
        try {
            log.info("[Kakao Notifier] Reminder sent to userKey: {}, timing: {}", userKey, timing);

            String message = switch (timing) {
                case MIN_15, HOUR_1 -> "투표가 시작됐어요! 다른 분들을 위해 빠른 참여 부탁드려요 :D";
                case HOUR_6 -> "다들 투표를 기다리고 있어요🙌";
                case HOUR_12 -> "웬디 기다리다 지쳐버림…🥹";
                case HOUR_24 -> "최후통첩✉️ 곧 투표가 마감됩니다!";
            };

            // TODO: 실제 카카오 메시지 API 호출
            log.info("[Kakao Notifier] Message: {}", message);

        } catch (Exception e) {
            log.error("[Kakao Notifier] Failed to send reminder: {}", e.getMessage());
        }
    }

    /**
     * 투표 결과 메시지 생성
     */
    public String buildVoteResultMessage(Long voteId) {
        VoteResultRes result = voteResultService.getVoteResult(voteId);

        if (result == null || result.rankings() == null || result.rankings().isEmpty()) {
            return "아직 투표 결과가 없어요.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 투표 현황\n\n");

        for (RankingRes ranking : result.rankings()) {
            String medal = switch (ranking.rank()) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "  ";
            };

            String dayLabel = getDayLabel(ranking.date().getDayOfWeek());
            String periodLabel = "LUNCH".equals(ranking.period()) ? "점심" : "저녁";

            sb.append(medal)
                    .append(" ")
                    .append(ranking.rank())
                    .append("위: ")
                    .append(ranking.date().format(DateTimeFormatter.ofPattern("MM/dd")))
                    .append("(")
                    .append(dayLabel)
                    .append(") ")
                    .append(periodLabel)
                    .append(" - ")
                    .append(ranking.voterCount())
                    .append("명\n");
        }

        return sb.toString();
    }

    /**
     * 미투표자 목록 조회
     */
    public List<String> getNonVoterNames(Long voteId) {
        List<ParticipantStatusRes> statuses = participantService.getParticipantStatusByVoteId(voteId);
        return statuses.stream()
                .filter(s -> !Boolean.TRUE.equals(s.submitted()))
                .map(ParticipantStatusRes::displayName)
                .collect(Collectors.toList());
    }

    /**
     * 카카오 메시지 API 호출 (템플릿)
     *
     * 참고: 실제 사용하려면 카카오 비즈메시지 설정 필요
     * - 카카오톡 채널 개설
     * - 발신 프로필 등록
     * - 알림톡 템플릿 승인
     */
    public boolean sendKakaoMessage(String userKey, String templateId, Map<String, String> templateArgs) {
        try {
            String adminKey = kakaoConfig.getAdminKey();
            if (adminKey == null || adminKey.isBlank()) {
                log.warn("[Kakao Notifier] Admin key not configured. Message not sent.");
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "KakaoAK " + adminKey);

            // 템플릿 기반 메시지 구성
            Map<String, Object> templateObject = new HashMap<>();
            templateObject.put("object_type", "text");
            templateObject.put("text", templateArgs.getOrDefault("message", "웬디 알림"));
            templateObject.put("link", Map.of(
                    "web_url", templateArgs.getOrDefault("link", "https://whendy.netlify.app"),
                    "mobile_web_url", templateArgs.getOrDefault("link", "https://whendy.netlify.app")
            ));

            String templateObjectJson = objectMapper.writeValueAsString(templateObject);
            String body = "receiver_uuids=[\"" + userKey + "\"]&template_object=" + templateObjectJson;

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = kakaoRestTemplate.exchange(
                    KAKAO_FRIEND_MESSAGE_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("[Kakao Notifier] Message sent. Response: {}", response.getBody());
            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            log.error("[Kakao Notifier] Failed to send Kakao message: {}", e.getMessage());
            return false;
        }
    }

    private String getDayLabel(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    public enum RemindTiming {
        MIN_15, HOUR_1, HOUR_6, HOUR_12, HOUR_24
    }
}