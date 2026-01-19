package com.workingdead.chatbot.kakao.service;

import com.workingdead.chatbot.kakao.dto.KakaoResponse;
import com.workingdead.meet.dto.ParticipantDtos.ParticipantStatusRes;
import com.workingdead.meet.dto.VoteDtos.CreateVoteReq;
import com.workingdead.meet.dto.VoteDtos.VoteSummary;
import com.workingdead.meet.dto.VoteResultDtos.RankingRes;
import com.workingdead.meet.dto.VoteResultDtos.VoteResultRes;
import com.workingdead.meet.service.ParticipantService;
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
 * 카카오 챗봇용 웬디 서비스
 * Discord와 독립적으로 세션 관리 (userKey 기반)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoWendyService {

    private final VoteService voteService;
    private final ParticipantService participantService;
    private final VoteResultService voteResultService;

    // 활성 세션 관리 (userKey 기반)
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    // 참석자 목록 (userKey -> List<이름>)
    private final Map<String, List<String>> participants = new ConcurrentHashMap<>();

    // 생성된 투표 ID (userKey -> voteId)
    private final Map<String, Long> userVoteId = new ConcurrentHashMap<>();

    // 생성된 투표 링크 (userKey -> shareUrl)
    private final Map<String, String> userShareUrl = new ConcurrentHashMap<>();

    // 투표 생성 시각 (userKey -> createdAt)
    private final Map<String, LocalDateTime> voteCreatedAt = new ConcurrentHashMap<>();

    // 세션 상태 (userKey -> state)
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    public enum SessionState {
        IDLE,
        WAITING_PARTICIPANTS,
        WAITING_WEEKS,
        VOTE_CREATED
    }

    // ========== 세션 관리 ==========

    /**
     * 세션 시작 (웬디 시작)
     */
    public KakaoResponse startSession(String userKey) {
        activeSessions.add(userKey);
        participants.put(userKey, new ArrayList<>());
        userVoteId.remove(userKey);
        userShareUrl.remove(userKey);
        voteCreatedAt.remove(userKey);
        sessionStates.put(userKey, SessionState.WAITING_PARTICIPANTS);

        log.info("[Kakao When:D] Session started: {}", userKey);

        return KakaoResponse.textWithQuickReplies(
                "안녕하세요! 일정 조율 도우미 웬디에요 :D\n" +
                "지금부터 여러분의 일정 조율을 도와드릴게요\n\n" +
                "먼저, 참석자 이름을 쉼표로 구분해서 입력해주세요!\n" +
                "예: 홍길동, 김철수, 이영희",
                List.of(
                        KakaoResponse.quickReply("도움말", "웬디 도움말"),
                        KakaoResponse.quickReply("종료", "웬디 종료")
                )
        );
    }

    /**
     * 세션 활성 여부 확인
     */
    public boolean isSessionActive(String userKey) {
        return activeSessions.contains(userKey);
    }

    /**
     * 세션 종료 (웬디 종료)
     */
    public KakaoResponse endSession(String userKey) {
        activeSessions.remove(userKey);
        participants.remove(userKey);
        userVoteId.remove(userKey);
        userShareUrl.remove(userKey);
        voteCreatedAt.remove(userKey);
        sessionStates.remove(userKey);

        log.info("[Kakao When:D] Session ended: {}", userKey);

        return KakaoResponse.simpleText(
                "웬디는 여기서 눈치껏 빠질게요 :D\n" +
                "모두 알찬 시간 보내세요!"
        );
    }

    /**
     * 현재 세션 상태 조회
     */
    public SessionState getSessionState(String userKey) {
        return sessionStates.getOrDefault(userKey, SessionState.IDLE);
    }

    // ========== 참석자 관리 ==========

    /**
     * 참석자 추가 (쉼표로 구분된 이름)
     */
    public KakaoResponse addParticipants(String userKey, String input) {
        List<String> names = Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (names.isEmpty()) {
            return KakaoResponse.simpleText(
                    "참석자 이름을 인식하지 못했어요.\n" +
                    "쉼표로 구분해서 다시 입력해주세요!\n" +
                    "예: 홍길동, 김철수, 이영희"
            );
        }

        participants.put(userKey, names);
        sessionStates.put(userKey, SessionState.WAITING_WEEKS);

        log.info("[Kakao When:D] Participants added: {} -> {}", userKey, names);

        return KakaoResponse.textWithQuickReplies(
                "참석자 " + names.size() + "명이 등록되었어요!\n" +
                "(" + String.join(", ", names) + ")\n\n" +
                "몇 주 뒤의 일정을 계획하시나요?",
                List.of(
                        KakaoResponse.quickReply("이번 주", "이번 주"),
                        KakaoResponse.quickReply("1주 뒤", "1주 뒤"),
                        KakaoResponse.quickReply("2주 뒤", "2주 뒤"),
                        KakaoResponse.quickReply("3주 뒤", "3주 뒤")
                )
        );
    }

    // ========== 투표 생성 ==========

    /**
     * 투표 생성 (주차 선택 후)
     */
    public KakaoResponse createVote(String userKey, int weeks) {
        voteCreatedAt.put(userKey, LocalDateTime.now());

        // 1. 날짜 범위 계산
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

        // 2. 참여자 이름 리스트
        List<String> participantNames = participants.getOrDefault(userKey, List.of());

        // 3. 투표 생성
        CreateVoteReq req = new CreateVoteReq(
                "카카오 투표",
                startDate,
                endDate,
                participantNames.isEmpty() ? null : participantNames
        );

        VoteSummary summary = voteService.create(req);
        Long voteId = summary.id();
        String shareUrl = summary.shareUrl();

        userVoteId.put(userKey, voteId);
        userShareUrl.put(userKey, shareUrl);
        sessionStates.put(userKey, SessionState.VOTE_CREATED);

        log.info("[Kakao When:D] Vote created: userKey={}, voteId={}, weeks={}", userKey, voteId, weeks);

        String weekLabel = weeks == 0 ? "이번 주" : weeks + "주 뒤";

        return KakaoResponse.basicCard(
                "투표가 생성되었어요!",
                weekLabel + " 일정 투표입니다.\n" +
                "(투표 늦게 하는 사람 대머리🧑‍🦲)\n\n" +
                "참석자: " + String.join(", ", participantNames),
                List.of(
                        KakaoResponse.webLinkButton("투표하러 가기", shareUrl),
                        KakaoResponse.messageButton("결과 확인", "웬디 결과")
                )
        );
    }

    /**
     * 주차 파싱 (0 = 이번 주, 1~6 = n주 뒤)
     */
    public Integer parseWeeks(String input) {
        if (input.contains("이번")) return 0;
        if (input.contains("1주")) return 1;
        if (input.contains("2주")) return 2;
        if (input.contains("3주")) return 3;
        if (input.contains("4주")) return 4;
        if (input.contains("5주")) return 5;
        if (input.contains("6주")) return 6;

        // 숫자만 추출
        String numbers = input.replaceAll("[^0-9]", "");
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
    public KakaoResponse getVoteResult(String userKey) {
        Long voteId = userVoteId.get(userKey);
        if (voteId == null) {
            return KakaoResponse.simpleText("아직 진행 중인 투표가 없어요.");
        }

        VoteResultRes result = voteResultService.getVoteResult(voteId);
        if (result == null || result.rankings() == null || result.rankings().isEmpty()) {
            return KakaoResponse.simpleText("아직 투표 결과가 없어요. 참석자들이 투표를 완료하면 결과를 확인할 수 있어요!");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 현재 투표 결과\n\n");

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

        // 미투표자 확인
        List<ParticipantStatusRes> statuses = participantService.getParticipantStatusByVoteId(voteId);
        List<String> nonVoters = statuses.stream()
                .filter(s -> !Boolean.TRUE.equals(s.submitted()))
                .map(ParticipantStatusRes::displayName)
                .collect(Collectors.toList());

        if (!nonVoters.isEmpty()) {
            sb.append("\n⏰ 아직 투표 안 한 사람: ")
                    .append(String.join(", ", nonVoters));
        }

        String shareUrl = userShareUrl.get(userKey);

        return KakaoResponse.basicCard(
                "투표 현황",
                sb.toString(),
                shareUrl != null ?
                        List.of(
                                KakaoResponse.webLinkButton("투표하러 가기", shareUrl),
                                KakaoResponse.messageButton("새로고침", "웬디 결과")
                        ) :
                        List.of(KakaoResponse.messageButton("새로고침", "웬디 결과"))
        );
    }

    /**
     * 재투표 (동일 참석자로 새 투표 생성)
     */
    public KakaoResponse revote(String userKey) {
        if (!userVoteId.containsKey(userKey)) {
            return KakaoResponse.simpleText("아직 진행된 투표가 없어요.");
        }

        userVoteId.remove(userKey);
        sessionStates.put(userKey, SessionState.WAITING_WEEKS);

        return KakaoResponse.textWithQuickReplies(
                "이전 참석자 명단으로 새 투표를 생성할게요!\n" +
                "몇 주 뒤의 일정을 계획하시나요?",
                List.of(
                        KakaoResponse.quickReply("이번 주", "이번 주"),
                        KakaoResponse.quickReply("1주 뒤", "1주 뒤"),
                        KakaoResponse.quickReply("2주 뒤", "2주 뒤"),
                        KakaoResponse.quickReply("3주 뒤", "3주 뒤")
                )
        );
    }

    /**
     * 도움말
     */
    public KakaoResponse help() {
        return KakaoResponse.textWithQuickReplies(
                "웬디는 다음과 같은 기능이 있어요!\n\n" +
                "📌 '웬디 시작': 일정 조율을 시작해요\n" +
                "📌 '웬디 종료': 작동을 종료해요\n" +
                "📌 '웬디 재투표': 동일한 참석자로 투표를 다시 올려요\n" +
                "📌 '웬디 결과': 현재 투표 현황을 확인해요",
                List.of(
                        KakaoResponse.quickReply("시작", "웬디 시작"),
                        KakaoResponse.quickReply("도움말", "웬디 도움말")
                )
        );
    }

    /**
     * 알 수 없는 입력 처리
     */
    public KakaoResponse unknownInput(String userKey) {
        SessionState state = getSessionState(userKey);

        return switch (state) {
            case WAITING_PARTICIPANTS -> KakaoResponse.simpleText(
                    "참석자 이름을 쉼표로 구분해서 입력해주세요!\n" +
                    "예: 홍길동, 김철수, 이영희"
            );
            case WAITING_WEEKS -> KakaoResponse.textWithQuickReplies(
                    "몇 주 뒤의 일정인지 선택해주세요!",
                    List.of(
                            KakaoResponse.quickReply("이번 주", "이번 주"),
                            KakaoResponse.quickReply("1주 뒤", "1주 뒤"),
                            KakaoResponse.quickReply("2주 뒤", "2주 뒤"),
                            KakaoResponse.quickReply("3주 뒤", "3주 뒤")
                    )
            );
            case VOTE_CREATED -> {
                String shareUrl = userShareUrl.get(userKey);
                yield KakaoResponse.textWithQuickReplies(
                        "투표가 진행 중이에요!\n" +
                        (shareUrl != null ? "투표 링크: " + shareUrl : ""),
                        List.of(
                                KakaoResponse.quickReply("결과 확인", "웬디 결과"),
                                KakaoResponse.quickReply("재투표", "웬디 재투표"),
                                KakaoResponse.quickReply("종료", "웬디 종료")
                        )
                );
            }
            default -> KakaoResponse.textWithQuickReplies(
                    "무엇을 도와드릴까요?",
                    List.of(
                            KakaoResponse.quickReply("시작", "웬디 시작"),
                            KakaoResponse.quickReply("도움말", "웬디 도움말")
                    )
            );
        };
    }

    // ========== 헬퍼 메서드 ==========

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
}