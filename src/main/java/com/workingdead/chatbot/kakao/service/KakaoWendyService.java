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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
     * 최후통첩 문구에 들어갈 '현재 1등 후보' 텍스트를 반환합니다.
     * - 결과 조회 로직(voteResultService.getVoteResult)을 그대로 재사용합니다.
     * - 반환 형식: "11/25(화) 점심" 또는 "11/25(화) 저녁"
     */
    public String getTopChoiceForFinal(Long voteId) {
        if (voteId == null) {
            throw new IllegalArgumentException("voteId must not be null");
        }

        VoteResultRes res = voteResultService.getVoteResult(voteId);
        if (res == null || res.rankings() == null || res.rankings().isEmpty()) {
            // 집계 불가 시 기본 문구(PRD에서 요구하는 형태로 대체 가능)
            return "00/00(월) 점심/저녁";
        }

        // rankings는 이미 rank 기준으로 정렬되어 온다고 가정하지만, 안전하게 1순위를 한 번 더 보장
        RankingRes r1 = res.rankings().stream()
                .filter(r -> r.rank() != null)
                .min(Comparator.comparingInt(RankingRes::rank))
                .orElse(res.rankings().get(0));

        String periodLabel = "LUNCH".equalsIgnoreCase(r1.period()) ? "점심" : "저녁";
        String dateText = (r1.date() == null) ? "00/00(월)" : String.valueOf(r1.date());
        return dateText + " " + periodLabel;
    }

    /**
     * PRD 2.4 최후통첩 메시지(링크 제외)를 동적으로 조립합니다.
     * - deadline: 최후통첩 발송 시각 기준 + 60분
     * - 확정 대상: 현재 1등 후보(날짜 + 점심/저녁)
     */
    public String buildFinalUltimatumMessage(Long voteId) {
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(60);
        String deadlineText = deadline.format(DateTimeFormatter.ofPattern("HH:mm"));
        String topChoice = getTopChoiceForFinal(voteId);

        return "스케쥴리도 이제 한계다. 그냥 투표하지 마라. 바쁘다 탓, 정신없다 탓하지 마라. 나도 충분히 기다려줬다.\n\n" +
                "나나 너나 현생 살기 바쁘고 연락 하나 하는 것도 에너지 써야 하는 힘든 시절인 거 안다. 그래서 이번 약속만큼은 우리끼리라도 즐겁게 시간 보내자고 제안했던 거다. 너에게 언제나 최고의 장소는 아니더라도 최선의 맛집을 찾아주고 싶었다.\n\n" +
                "내가 업무에 치이고 잠 줄여가며 네 답장 기다리고, 식당 예약 알아보고, 일정 조율하는 거, 이거 다 우리 좋은 시간 보내게 해주고 싶어서였다. 네가 읽씹하거나 답장이 늦을 때도, 겉으로는 \"바쁜가 보다\" 했지만 뒤에서는 '내가 너무 보채나' 하며 휴대폰만 만지작거렸다. 그래도 우리 만나면 즐겁겠지, 만나서 회포 풀면 스트레스 풀리겠지. 이 생각만 하며 꾹 참으며 며칠을 보냈다.\n\n" +
                "그런데 이게 뭐냐? 너 지금 투표 올라온 지 몇 시간인지 알긴 하냐? 도대체 그 나이에 약속 하나 확답하는 게 그렇게 힘든 일이란 말이냐? 늘 만나자고는 말만 하면서 정작 실천하는 게 뭐냔 말이다.\n\n" +
                "오늘 문득 너랑 약속 잡으려고 안달 난 내가 잘못했다는 생각이 든다. 거울 속 핸드폰만 붙잡고 있는 내 모습에 눈물이 나더라. 그냥... 이제 투표하지 마라. 나를 원망하지도 말고 네 스케줄대로 알아서 살아라. 나도 지쳤다. 당장 톡방을 나가라.\n\n" +
                "📩 최후통첩\n\n" +
                deadlineText + "까지 투표 불참 시, " + topChoice + "으로 확정할게요!😤";
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

    // ========== 투표 완료 (PRD 2.5) ==========

    /**
     * 투표 완료 메시지 빌드
     * - 상위 3순위 결과 + 투표자 목록 포맷팅
     */
    public String buildVoteCompletionMessage(Long voteId) {
        VoteResultRes result = voteResultService.getVoteResult(voteId);

        StringBuilder sb = new StringBuilder();
        sb.append("투표가 완료됐어요! :D\n");

        if (result != null && result.rankings() != null && !result.rankings().isEmpty()) {
            List<RankingRes> top3 = result.rankings().stream()
                    .filter(r -> r.rank() != null && r.rank() >= 1 && r.rank() <= 3)
                    .sorted(Comparator.comparingInt(RankingRes::rank))
                    .toList();

            for (RankingRes rank : top3) {
                String periodLabel = "LUNCH".equalsIgnoreCase(rank.period()) ? "점심" : "저녁";
                String dateText = formatDateKorean(rank.date());

                sb.append("\n📌").append(rank.rank()).append("순위 ")
                        .append(dateText).append(" ").append(periodLabel).append("\n");

                if (rank.voters() != null && !rank.voters().isEmpty()) {
                    String voterStr = rank.voters().stream()
                            .map(v -> v.participantName()
                                    + (v.priorityIndex() != null ? "(" + v.priorityIndex() + ")" : ""))
                            .collect(Collectors.joining(", "));
                    sb.append("투표자: ").append(voterStr).append("\n");
                }
            }
        }

        sb.append("\n이제 몇 시에 만날지 정해볼까요?🙂");
        return sb.toString();
    }

    private String formatDateKorean(LocalDate date) {
        if (date == null) return "??/??(?)";
        String[] dayNames = {"월", "화", "수", "목", "금", "토", "일"};
        String dayOfWeek = dayNames[date.getDayOfWeek().getValue() - 1];
        return date.getMonthValue() + "/" + date.getDayOfMonth() + "(" + dayOfWeek + ")";
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