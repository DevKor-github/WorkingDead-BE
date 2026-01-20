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
 * 카카오 Bot API를 통해 그룹 채팅방에 이벤트 메시지를 전송합니다.
 * - Event API: 그룹 채팅방에 Push 메시지 전송
 * - 개인챗은 스킬 응답으로만 메시지 전송 가능 (Pull 방식)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoNotifier {

    private final KakaoConfig kakaoConfig;
    private final KakaoWendyService kakaoWendyService;
    private final KakaoBotApiClient kakaoBotApiClient;
    private final VoteResultService voteResultService;
    private final ParticipantService participantService;
    private final RestTemplate kakaoRestTemplate;
    private final ObjectMapper objectMapper;

    // ========== Event API (그룹 채팅방 메시지 발송) ==========

    /**
     * 그룹 채팅방에 이벤트 메시지 발송
     *
     * @param botGroupKey 채팅방 키
     * @param eventName   관리자센터에 등록된 이벤트 블록 이름
     */
    public void sendEventToGroup(String botGroupKey, String eventName) {
        try {
            if (botGroupKey == null || botGroupKey.isBlank()) {
                log.warn("[Kakao Notifier] botGroupKey is empty. Cannot send event message.");
                return;
            }

            KakaoBotApiClient.EventResponse response =
                    kakaoBotApiClient.sendEventMessage(List.of(botGroupKey), eventName);
            log.info("[Kakao Notifier] Event sent: botGroupKey={}, eventName={}, taskId={}",
                    botGroupKey, eventName, response.getTaskId());

        } catch (Exception e) {
            log.error("[Kakao Notifier] Failed to send event message: {}", e.getMessage());
        }
    }

    /**
     * 투표 현황 공유 (이벤트 메시지)
     */
    public void shareVoteStatus(String sessionKey) {
        try {
            Long voteId = kakaoWendyService.getVoteIdBySessionKey(sessionKey);
            if (voteId == null) {
                log.warn("[Kakao Notifier] No vote found for sessionKey: {}", sessionKey);
                return;
            }

            // botGroupKey 조회 (그룹챗인 경우에만 이벤트 발송)
            String botGroupKey = kakaoWendyService.getBotGroupKeyByVoteId(voteId);
            if (botGroupKey != null) {
                sendEventToGroup(botGroupKey, "vote_status");  // 관리자센터에서 설정한 이벤트 블록 이름
            } else {
                log.info("[Kakao Notifier] Vote status share requested for individual chat: {}", sessionKey);
            }

        } catch (Exception e) {
            log.error("[Kakao Notifier] Failed to share vote status: {}", e.getMessage());
        }
    }

    /**
     * 미투표자 리마인드 (이벤트 메시지)
     */
    public void remindNonVoters(String sessionKey, RemindTiming timing) {
        try {
            Long voteId = kakaoWendyService.getVoteIdBySessionKey(sessionKey);
            if (voteId == null) {
                log.warn("[Kakao Notifier] No vote found for sessionKey: {}", sessionKey);
                return;
            }

            String eventName = switch (timing) {
                case MIN_15 -> "remind_15min";
                case HOUR_1 -> "remind_1hour";
                case HOUR_6 -> "remind_6hour";
                case HOUR_12 -> "remind_12hour";
                case HOUR_24 -> "remind_24hour";
            };

            // botGroupKey 조회 (그룹챗인 경우에만 이벤트 발송)
            String botGroupKey = kakaoWendyService.getBotGroupKeyByVoteId(voteId);
            if (botGroupKey != null) {
                sendEventToGroup(botGroupKey, eventName);
            } else {
                log.info("[Kakao Notifier] Reminder for individual chat (cannot push): sessionKey={}, timing={}",
                        sessionKey, timing);
            }

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
                    .append(ranking.voteCount())
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