package com.workingdead.chatbot.kakao.service;

import com.workingdead.config.KakaoConfig;
import com.workingdead.meet.dto.ParticipantDtos.ParticipantStatusRes;
import com.workingdead.meet.entity.NotificationType;
import com.workingdead.meet.entity.Vote;
import com.workingdead.meet.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoNotifier {

    private final KakaoConfig kakaoConfig;
    private final ParticipantService participantService;

    /**
     * Kakao Event API base URL
     */
    private static final String BASE_URL = "https://bot-api.kakao.com";

    /**
     * (Guide) Event API: POST /v2/bots/{botId}/group
     * - Authorization: KakaoAK {REST API Key}
     * - Body: { chat: [{id, type}], event: {name, data} }
     */
    public void sendEventToGroup(String botGroupKey, String eventName, Map<String, Object> eventData) {
        if (botGroupKey == null || botGroupKey.isBlank()) {
            log.warn("[Kakao Notifier] botGroupKey is empty. Skip send.");
            return;
        }
        if (eventName == null || eventName.isBlank()) {
            log.warn("[Kakao Notifier] eventName is empty. Skip send.");
            return;
        }

        try {
            String botId = kakaoConfig.getBotId();
            String restApiKey = kakaoConfig.getRestApiKey();

            Map<String, Object> body = new HashMap<>();

            Map<String, Object> chat = new HashMap<>();
            chat.put("id", botGroupKey);
            chat.put("type", "botGroupKey");
            body.put("chat", List.of(chat));

            Map<String, Object> event = new HashMap<>();
            event.put("name", eventName);
            event.put("data", eventData == null ? Map.of() : eventData);
            body.put("event", event);

            RestClient client = RestClient.builder()
                    .baseUrl(BASE_URL)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map res = client.post()
                    .uri("/v2/bots/{botId}/group", botId)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            Object taskId = res == null ? null : res.get("taskId");
            Object status = res == null ? null : res.get("status");
            log.info("[Kakao Notifier] Event sent: botGroupKey={}, eventName={}, taskId={}, status={}",
                    botGroupKey, eventName, taskId, status);

        } catch (Exception e) {
            log.error("[Kakao Notifier] Failed to send event: botGroupKey={}, eventName={}", botGroupKey, eventName, e);
        }
    }

    /** 편의 오버로드 (data 없는 이벤트) */
    public void sendEventToGroup(String botGroupKey, String eventName) {
        sendEventToGroup(botGroupKey, eventName, Map.of());
    }

    // ========== Scheduler가 호출하는 Vote-centric API ==========

    /** 미투표자 존재 여부 */
    public boolean hasNonVoters(Vote vote) {
        if (vote == null) return false;
        List<ParticipantStatusRes> statuses = participantService.getParticipantStatusByVoteId(vote.getId());
        return statuses.stream().anyMatch(s -> !Boolean.TRUE.equals(s.submitted()));
    }

    /** 전체 투표 완료 여부 */
    public boolean isAllVoted(Vote vote) {
        if (vote == null) return false;
        List<ParticipantStatusRes> statuses = participantService.getParticipantStatusByVoteId(vote.getId());
        if (statuses.isEmpty()) return false;
        return statuses.stream().allMatch(s -> Boolean.TRUE.equals(s.submitted()));
    }

    /** (PRD 2.2) 투표 현황 공유 */
    public void shareVoteStatus(Vote vote) {
        if (vote == null) return;
        String botGroupKey = vote.getBotGroupKey();

        List<ParticipantStatusRes> statuses = participantService.getParticipantStatusByVoteId(vote.getId());
        long submitted = statuses.stream().filter(s -> Boolean.TRUE.equals(s.submitted())).count();

        // 블록은 고정, 서버는 분기만: 아무도 투표 안함 vs 결과 존재
        String eventName = (submitted == 0) ? "status_nobody_voted" : "status_vote_result";

        Map<String, Object> data = new HashMap<>();
        data.put("voteId", String.valueOf(vote.getId()));
        data.put("submittedCount", submitted);
        data.put("totalCount", statuses.size());

        sendEventToGroup(botGroupKey, eventName, data);

        // 전원 완료면 별도 이벤트 (필요하면)
        if (!statuses.isEmpty() && submitted == statuses.size()) {
            sendEventToGroup(botGroupKey, "status_all_done", data);
        }
    }

    /** (PRD 2.3) 독촉 */
    public void remindNonVoters(Vote vote, NotificationType type) {
        if (vote == null) return;

        // 스킬에서 멘션/문구를 생성하므로, 여기서는 이벤트 블록 트리거만 수행
        String eventName = switch (type) {
            case NUDGE_30M -> "remind_30min";
            case NUDGE_2H -> "remind_2hour";
            case NUDGE_6H -> "remind_6hour";
            case NUDGE_12H -> "remind_12hour";
            default -> "remind_30min"; // fallback
        };

        Map<String, Object> data = new HashMap<>();
        data.put("voteId", String.valueOf(vote.getId()));
        // 스킬에서 timing에 따라 고정 문구를 선택할 수 있게 전달
        data.put("timing", type.name());

        sendEventToGroup(vote.getBotGroupKey(), eventName, data);
    }

    /** (PRD 2.4) 최후통첩 */
    public void sendFinalNotice(Vote vote) {
        if (vote == null) return;

        String botGroupKey = vote.getBotGroupKey();
        if (botGroupKey == null || botGroupKey.isBlank()) {
            log.warn("[Kakao Notifier] botGroupKey is empty. Skip final notice. voteId={}", vote.getId());
            return;
        }

        // 스킬(/kakao/skill/notify/final)에서 voteId로 1등 후보 + deadline(now+60m)를 계산한다.
        Map<String, Object> data = new HashMap<>();
        data.put("voteId", String.valueOf(vote.getId()));
        data.put("timing", NotificationType.ULTIMATUM_24H.name());

        sendEventToGroup(botGroupKey, "final_notice_24h", data);
    }

    /** (PRD 2.5) 모두 투표 완료 */
    public void sendAllVoted(Vote vote) {
        if (vote == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("voteId", String.valueOf(vote.getId()));
        sendEventToGroup(vote.getBotGroupKey(), "done_all_voted", data);
    }

    /**
     * 미투표자 이름 목록
     * - displayName이 null/blank면 "미등록"으로 표시
     */
    public List<String> getNonVoterNames(Long voteId) {
        List<ParticipantStatusRes> statuses = participantService.getParticipantStatusByVoteId(voteId);
        return statuses.stream()
                .filter(s -> !Boolean.TRUE.equals(s.submitted()))
                .map(ParticipantStatusRes::displayName)
                .map(name -> (name == null || name.isBlank()) ? "미등록" : name)
                .collect(Collectors.toList());
    }
}