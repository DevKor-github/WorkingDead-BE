package com.workingdead.chatbot.kakao.service;

import com.workingdead.chatbot.kakao.client.KakaoChatClient;
import com.workingdead.chatbot.kakao.client.KakaoChatUser;
import com.workingdead.chatbot.kakao.dto.KakaoResponse;
import com.workingdead.meet.dto.VoteDtos.CreateVoteReq;
import com.workingdead.meet.dto.VoteDtos.VoteSummary;
import com.workingdead.meet.dto.VoteResultDtos.RankingRes;
import com.workingdead.meet.dto.VoteResultDtos.VoteResultRes;
import com.workingdead.meet.service.VoteResultService;
import com.workingdead.meet.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * - 그룹챗 전용: botGroupKey 기반
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoWendyService {

    private final VoteService voteService;
    private final VoteResultService voteResultService;

    // ========== 세션 관리 (key = botGroupKey) ==========

    // 활성 세션 관리
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    // 생성된 투표 ID (botGroupKey -> voteId)
    private final Map<String, Long> sessionVoteId = new ConcurrentHashMap<>();
    // 세션 상태 (botGroupKey -> state)
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();
    private final KakaoChatClient kakaoChatClient;

    public enum SessionState {
        IDLE,
        WAITING_WEEKS,
        VOTE_CREATED
    }

    /**
     * 세션 시작 (@웬디 시작)
     * - 그룹챗 전용: botGroupKey를 세션 키로 사용
     * - 다음 단계: 주차(기간) 선택 대기
     */
    public KakaoResponse startSession(String botGroupKey) {
        activeSessions.add(botGroupKey);
        sessionVoteId.remove(botGroupKey);
        sessionStates.put(botGroupKey, SessionState.WAITING_WEEKS);

        log.info("[Kakao When:D] Session started: botGroupKey={}", botGroupKey);

        Map<String, Object> data = new HashMap<>();
        data.put("botGroupKey", botGroupKey);
        data.put("state", SessionState.WAITING_WEEKS.name());
        data.put("active", true);
        return dataOnly(data);
    }

    /**
     * 세션 활성 여부 확인
     */
    public boolean isSessionActive(String botGroupKey) {
        return activeSessions.contains(botGroupKey);
    }

    /**
     * 세션 종료 (웬디 종료)
     */
    public KakaoResponse endSession(String botGroupKey) {
        Long voteId = sessionVoteId.get(botGroupKey);

        if (voteId != null) {
            voteService.closeVote(voteId); // status = CLOSED
            log.info("[Kakao When:D] Vote closed: voteId={}", voteId);
        }

        activeSessions.remove(botGroupKey);
        sessionVoteId.remove(botGroupKey);
        sessionStates.remove(botGroupKey);

        log.info("[Kakao When:D] Session ended: botGroupKey={}", botGroupKey);

        Map<String, Object> data = new HashMap<>();
        data.put("botGroupKey", botGroupKey);
        data.put("state", SessionState.IDLE.name());
        data.put("active", false);
        return dataOnly(data);
    }

    /**
     * 현재 세션 상태 조회
     */
    public SessionState getSessionState(String botGroupKey) {
        return sessionStates.getOrDefault(botGroupKey, SessionState.IDLE);
    }

    // 참석자 관리 섹션 삭제됨

    // ========== 투표 생성 ==========

    /**
     * 투표 생성 (주차/기간 선택 후)
     * - 사용자 입력(weeks)을 기준으로 서버에서 날짜 범위를 계산합니다.
     * - Vote 엔티티에 botGroupKey를 포함하여 저장해야 합니다. (Vote.botGroupKey, status=ACTIVE)
     * - 사용자에게는 고정 리다이렉트 엔드포인트(/open-vote) 링크를 제공합니다.
     */
    public KakaoResponse createVote(String botGroupKey, int weeks) {
        // 날짜 범위 계산
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        if (weeks == 0) {
            startDate = today;
            int daysToSunday = DayOfWeek.SUNDAY.getValue() - today.getDayOfWeek().getValue();
            endDate = today.plusDays(Math.max(daysToSunday, 0));
        } else {
            LocalDate mondayThisWeek = today.with(DayOfWeek.MONDAY);
            startDate = mondayThisWeek.plusWeeks(weeks);
            endDate = startDate.plusDays(6);
        }

        // 세션 상태 정리
        activeSessions.add(botGroupKey);
        sessionStates.put(botGroupKey, SessionState.VOTE_CREATED);

        CreateVoteReq req = new CreateVoteReq(
                "카카오 투표",
                startDate,
                endDate,
                null
        );

        // 채팅방 참여자 목록 조회 (botUserKey 리스트)
        List<String> botUserKeys;
        try {
            botUserKeys = kakaoChatClient.fetchChatUsers(botGroupKey).stream()
                    .map(KakaoChatUser::botUserKey)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.error("[Kakao When:D] Failed to fetch chat members: botGroupKey={}", botGroupKey, e);
            Map<String, Object> err = new HashMap<>();
            err.put("botGroupKey", botGroupKey);
            err.put("state", SessionState.WAITING_WEEKS.name());
            err.put("error", "채팅방 참여자 목록 조회에 실패했어요. 잠시 후 다시 시도해주세요.");
            return dataOnly(err);
        }

        VoteSummary summary = voteService.createKakaoVote(req, botGroupKey, botUserKeys);        Long voteId = summary.id();

        // 고정 리다이렉트 링크 (카카오가 botGroupKey/botUserKey/appUserId를 자동 append)
        String redirectUrl = "/open-vote";

        sessionVoteId.put(botGroupKey, voteId);

        log.info("[Kakao When:D] Vote created: botGroupKey={}, voteId={}, startDate={}, endDate={}, weeks={}",
                botGroupKey, voteId, startDate, endDate, weeks);

        Map<String, Object> data = new HashMap<>();
        data.put("botGroupKey", botGroupKey);
        data.put("voteId", voteId);
        data.put("memberCount", botUserKeys.size());
        data.put("redirectUrl", redirectUrl);
        data.put("startDate", startDate.toString());
        data.put("endDate", endDate.toString());
        data.put("weeks", weeks);
        data.put("state", SessionState.VOTE_CREATED.name());
        return dataOnly(data);
    }

    /**
     * 주차 파싱 (0 = 이번 주, 1~6 = n주 뒤)
     */
    public Integer parseWeeks(String input) {
        if (input == null || input.isBlank()) return null;

        String s = input.trim();

        // 자주 쓰는 자연어 표현
        if (s.contains("이번")) return 0;
        if (s.contains("다다음")) return 2;
        if (s.contains("다음")) return 1;

        // 명시적 "n주" 표현
        if (s.contains("1주")) return 1;
        if (s.contains("2주")) return 2;
        if (s.contains("3주")) return 3;
        if (s.contains("4주")) return 4;
        if (s.contains("5주")) return 5;
        if (s.contains("6주")) return 6;

        // 숫자만 추출 (예: "2주 후", "3주뒤" 등)
        String numbers = s.replaceAll("[^0-9]", "");
        if (!numbers.isEmpty()) {
            try {
                int weeks = Integer.parseInt(numbers);
                if (weeks >= 0 && weeks <= 6) return weeks;
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    // ========== 결과 조회 ==========

    /**
     * 투표 결과 조회
     */
    public KakaoResponse getVoteResult(String botGroupKey) {
        Long voteId = sessionVoteId.get(botGroupKey);
        String redirectUrl = "/open-vote";

        if (voteId == null) {
            return textOnly("""
                    웬디가 투표 현황을 공유드려요! :D

                    아직 진행 중인 투표가 없어요 :(
                    """.strip());
        }

        VoteResultRes result = voteResultService.getVoteResult(voteId);

        if (result == null || result.rankings() == null || result.rankings().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("웬디가 투표 현황을 공유드려요! :D\n\n");
            sb.append("엥 아직 아무도 투표를 안 했네요 :(\n");
            sb.append("\n투표하러 가기: ").append(redirectUrl);
            return textOnly(sb.toString().trim());
        }

        // 1~3순위만 출력 (없는 순위는 생략)
        List<RankingRes> top3 = result.rankings().stream()
                .filter(r -> r.rank() != null)
                .filter(r -> r.rank() >= 1 && r.rank() <= 3)
                .sorted(Comparator.comparingInt(RankingRes::rank))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("웬디가 투표 현황을 공유드려요! :D\n");
        sb.append("\n투표하러 가기: ").append(redirectUrl).append("\n\n");

        for (RankingRes rank : top3) {
            String periodLabel = "LUNCH".equals(rank.period()) ? "점심" : "저녁";

            sb.append("📌")
                    .append(rank.rank()).append("순위 ")
                    .append(rank.date()).append(" ")
                    .append(periodLabel).append("\n");

            if (rank.voters() != null && !rank.voters().isEmpty()) {
                String voterStr = rank.voters().stream()
                        .map(v -> v.participantName()
                                + (v.priorityIndex() != null ? "(" + v.priorityIndex() + ")" : ""))
                        .collect(Collectors.joining(", "));
                sb.append("투표자: ").append(voterStr).append("\n");
            }
            sb.append("\n");
        }

        // top3 가 비어있으면(이상 케이스) 그래도 안전하게 메시지 출력
        if (top3.isEmpty()) {
            sb.append("아직 집계할 수 있는 순위 결과가 없어요 :(");
        }

        return textOnly(sb.toString().trim());
    }

    /**
     * 재투표 (세션 상태를 WAITING_WEEKS로 되돌리고 voteId를 제거)
     */
    public KakaoResponse revote(String botGroupKey) {
        if (!sessionVoteId.containsKey(botGroupKey)) {
            activeSessions.add(botGroupKey);
            sessionStates.put(botGroupKey, SessionState.WAITING_WEEKS);
            Map<String, Object> data = new HashMap<>();
            data.put("hasVote", false);
            data.put("state", SessionState.WAITING_WEEKS.name());
            return dataOnly(data);
        }

        sessionVoteId.remove(botGroupKey);
        activeSessions.add(botGroupKey);
        sessionStates.put(botGroupKey, SessionState.WAITING_WEEKS);

        Map<String, Object> data = new HashMap<>();
        data.put("hasVote", true);
        data.put("state", SessionState.WAITING_WEEKS.name());
        return dataOnly(data);
    }

    /**
     * 도움말
     */
    public KakaoResponse help() {
        Map<String, Object> data = new HashMap<>();
        data.put("commands", List.of("시작", "종료", "재투표", "결과"));
        return dataOnly(data);
    }

    /**
     * 알 수 없는 입력 처리
     */
    public KakaoResponse unknownInput(String botGroupKey) {
        SessionState state = getSessionState(botGroupKey);
        Map<String, Object> data = new HashMap<>();
        data.put("state", state.name());
        Long voteId = sessionVoteId.get(botGroupKey);
        if (voteId != null) {
            data.put("voteId", voteId);
            data.put("redirectUrl", "/open-vote");
        }
        data.put("botGroupKey", botGroupKey);
        return dataOnly(data);
    }

    public Long getVoteIdByBotGroupKey(String botGroupKey) {
        return sessionVoteId.get(botGroupKey);
    }

    // ========== 헬퍼 메서드 ==========

    private KakaoResponse dataOnly(Map<String, Object> data) {
        Map<String, Object> safe = (data == null) ? new HashMap<>() : data;
        return KakaoResponse.builder()
                .version("2.0")
                .data(safe)
                .build();
    }

    private KakaoResponse textOnly(String text) {
        // 결과 조회는 블록 멘트가 아니라 스킬 응답(simpleText)로 바로 출력
        return KakaoResponse.builder()
                .version("2.0")
                .template(KakaoResponse.Template.builder()
                        .outputs(List.of(
                                KakaoResponse.Output.builder()
                                        .simpleText(KakaoResponse.SimpleText.builder()
                                                .text(text)
                                                .build())
                                        .build()
                        ))
                        .build())
                .build();
    }

}