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
    public void shareVoteStatus(String botGroupKey) {
        try {
            Long voteId = kakaoWendyService.getVoteIdByBotGroupKey(botGroupKey);
            if (voteId == null) return;

            List<ParticipantStatusRes> statuses = participantService.getParticipantStatusByVoteId(voteId);
            long submittedCount = statuses.stream().filter(s -> Boolean.TRUE.equals(s.submitted())).count();
            long totalCount = statuses.size();

            // 참가자 자체가 없는 비정상 케이스(생성 꼬임 등) 방어
            if (totalCount == 0) {
                log.warn("[Kakao Notifier] No participants found for voteId={}", voteId);
                return;
            }

            if (submittedCount == 0) {
                sendEventToGroup(botGroupKey, "status_nobody_voted");
            } else {
                sendEventToGroup(botGroupKey, "status_vote_result");
            }

            boolean allSubmitted = totalCount > 0 && submittedCount == totalCount;
            if (allSubmitted) {
                sendEventToGroup(botGroupKey, "status_all_done");
            }

        } catch (Exception e) {
            log.error("[Kakao Notifier] Failed to share vote status: {}", e.getMessage());
        }
    }

    public void sendFinalNotice(String botGroupKey) {
        try {
            Long voteId = kakaoWendyService.getVoteIdByBotGroupKey(botGroupKey);
            if (voteId == null) return;

            List<String> nonVoters = getNonVoterNames(voteId);
            if (nonVoters.isEmpty()) return; // 미투표자 없으면 전송 X

            sendEventToGroup(botGroupKey, "final_notice_24h");
        } catch (Exception e) {
            log.error("[Kakao Notifier] sendFinalNotice failed: {}", e.getMessage(), e);
        }
    }

    public void finalizeIfNoResponse(String botGroupKey) {
        try {
            Long voteId = kakaoWendyService.getVoteIdByBotGroupKey(botGroupKey);
            if (voteId == null) return;

            List<String> nonVoters = getNonVoterNames(voteId);
            if (nonVoters.isEmpty()) return; // 이미 다 했으면 확정 처리 X

            sendEventToGroup(botGroupKey, "finalize_after_60m");

        } catch (Exception e) {
            log.error("[Kakao Notifier] finalizeIfNoResponse failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 미투표자 리마인드 (이벤트 메시지)
     */
    public void remindNonVoters(String botGroupKey, RemindTiming timing) {
        try {
            Long voteId = kakaoWendyService.getVoteIdByBotGroupKey(botGroupKey);
            if (voteId == null) {
                log.warn("[Kakao Notifier] No vote found for botGroupKey: {}", botGroupKey);
                return;
            }

            List<String> nonVoters = getNonVoterNames(voteId);
            if (nonVoters.isEmpty()) {
                log.info("[Kakao Notifier] No non-voters. Skip reminder: botGroupKey={}, timing={}", botGroupKey, timing);
                return;
            }

            String eventName = switch (timing) {
                case MIN_30 -> "remind_30min";
                case HOUR_2 -> "remind_2hour";
                case HOUR_6 -> "remind_6hour";
                case HOUR_12 -> "remind_12hour";
            };

            sendEventToGroup(botGroupKey, eventName);

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
        MIN_30, HOUR_2, HOUR_6, HOUR_12
    }
}