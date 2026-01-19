package com.workingdead.chatbot.service;

import com.workingdead.meet.dto.ParticipantDtos.ParticipantRes;
import com.workingdead.meet.dto.ParticipantDtos.ParticipantStatusRes;
import com.workingdead.meet.dto.VoteDtos.CreateVoteReq;
import com.workingdead.meet.dto.VoteDtos.VoteSummary;
import com.workingdead.meet.dto.VoteResultDtos.RankingRes;
import com.workingdead.meet.dto.VoteResultDtos.VoteResultRes;
import com.workingdead.meet.service.ParticipantService;
import com.workingdead.meet.service.VoteResultService;
import com.workingdead.meet.service.VoteService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Service
@RequiredArgsConstructor
public class WendyServiceImpl implements WendyService {

    private final VoteService voteService;
    private final ParticipantService participantService;
    private final VoteResultService voteResultService;

    // 활성 세션 관리 (channelId 기반)
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    // 디스코드 참석자 (channelId -> (discordUserId -> displayName))
    private final Map<String, Map<String, String>> participants = new ConcurrentHashMap<>();

    // 생성된 투표 id (channelId -> voteId)
    private final Map<String, Long> channelVoteId = new ConcurrentHashMap<>();

    // 생성된 투표 링크
    private final Map<String, String> channelShareUrl = new ConcurrentHashMap<>();
    
    // 투표 생성 여부 (재투표 체크용)
    private final Set<String> hasVote = ConcurrentHashMap.newKeySet();

    // 투표 생성 시각 및 기준 주차
    private final Map<String, LocalDateTime> voteCreatedAt = new ConcurrentHashMap<>();
    private final Map<String, Integer> voteWeeks = new ConcurrentHashMap<>();

    @Override
    public void startSession(String channelId, List<Member> members) {
        activeSessions.add(channelId);

        participants.put(channelId, new ConcurrentHashMap<>());

        channelVoteId.remove(channelId);
        channelShareUrl.remove(channelId);

        voteCreatedAt.remove(channelId);
        voteWeeks.remove(channelId);
        System.out.println("[When:D] Session started: " + channelId);
    }
    
    @Override
    public boolean isSessionActive(String channelId) {
        return activeSessions.contains(channelId);
    }
    
    @Override
    public void endSession(String channelId) {
        activeSessions.remove(channelId);
        participants.remove(channelId);

        channelVoteId.remove(channelId);
        channelShareUrl.remove(channelId);

        hasVote.remove(channelId);
        voteCreatedAt.remove(channelId);
        voteWeeks.remove(channelId);
        System.out.println("[When:D] Session ended: " + channelId);
    }
    
    @Override
    public void addParticipant(String channelId, String memberId, String memberName) {
        // 1. 디스코드 참석자 목록에 추가 (기존 값이 있었는지 확인)
        Map<String, String> channelParticipants =
                participants.computeIfAbsent(channelId, k -> new ConcurrentHashMap<>());
        String previousName = channelParticipants.put(memberId, memberName);

        Long voteId = channelVoteId.get(channelId);
        if (voteId == null) {
            // 아직 투표가 생성되지 않았다면, createVote 시점에 한 번에 도메인 Participant 생성
            System.out.println("[When:D] Participant added BEFORE vote: " + memberName
                    + " (discordId=" + memberId + ")");
            return;
        }

        // 이미 이 디스코드 유저가 참석자 목록에 있었던 경우라면,
        // (초기 참석자이거나, 이전에 한 번 추가되었던 경우) 도메인 Participant를 중복 생성하지 않는다.
        if (previousName != null) {
            System.out.println("[When:D] Participant already exists AFTER vote: " + memberName
                    + " (discordId=" + memberId + ")");
            return;
        }

        // 2. 투표가 생성된 이후 처음 합류하는 참석자에 대해서만 도메인 Participant 생성
        ParticipantRes pRes = participantService.add(voteId, memberName);
        System.out.println("[When:D] Participant added AFTER vote: " + memberName
                + " (discordId=" + memberId + ", participantId=" + pRes.id() + ")");
    }

    @Override
    public void removeParticipant(String channelId, String memberId) {
        Map<String, String> channelParticipants = participants.get(channelId);
        String removedName = null;
        if (channelParticipants != null) {
            removedName = channelParticipants.remove(memberId);
        }

        Long voteId = channelVoteId.get(channelId);
        if (voteId == null) {
            System.out.println("[When:D] Participant removed BEFORE vote: "
                    + (removedName != null ? removedName : memberId)
                    + " (discordId=" + memberId + ")");
        } else {
            // 현재는 디스코드 쪽 참석자 목록에서만 제거
            System.out.println("[When:D] Participant removed AFTER vote (domain not deleted): "
                    + (removedName != null ? removedName : memberId)
                    + " (discordId=" + memberId + ")");
        }
    }


    @Override
    public String createVote(String channelId, String channelName, int weeks) {
        hasVote.add(channelId);
        voteCreatedAt.put(channelId, LocalDateTime.now());
        voteWeeks.put(channelId, weeks);

        // 1. 날짜 범위 계산
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        if (weeks == 0) {
            // 이번 주: 오늘 ~ 이번주 일요일
            startDate = today;
            int daysToSunday = DayOfWeek.SUNDAY.getValue() - today.getDayOfWeek().getValue();
            endDate = today.plusDays(Math.max(daysToSunday, 0));
        } else  {
            // n주 후: n주 뒤 월요일 ~ 일요일
            LocalDate mondayThisWeek = today.with(DayOfWeek.MONDAY);
            startDate = mondayThisWeek.plusWeeks(weeks);
            endDate = startDate.plusDays(6);
        }

        // 2. 참여자 이름 리스트 만들기
        Map<String, String> channelParticipants = participants.getOrDefault(channelId, Map.of());
        List<String> participantNames = new ArrayList<>(channelParticipants.values());

        // 3. 투표 생성 DTO 구성
        CreateVoteReq req = new CreateVoteReq(
                channelName,
                startDate,
                endDate,
                participantNames.isEmpty() ? null : participantNames
        );

        // 4. 투표 생성
        VoteSummary summary = voteService.create(req);
        Long voteId = summary.id();
        String shareUrl = summary.shareUrl();
        channelShareUrl.put(channelId, shareUrl);

        // 5. channelId -> voteId 매핑 저장
        channelVoteId.put(channelId, voteId);


        System.out.println("[When:D] Vote created for channel " + channelId + " (voteId=" + voteId
                + ", (weeks=" + weeks + "))");
        return shareUrl;
    }
    
    @Override
    public VoteResultRes getVoteStatus(String channelId) {
        Long voteId = channelVoteId.get(channelId);
        if (voteId == null) {
            return null;
        }

        return voteResultService.getVoteResult(voteId);
    }

    @Override
    public List<String> getNonVoterIds(String channelId) {
        Long voteId = channelVoteId.get(channelId);
        if (voteId == null) {
            return List.of();
        }

        Map<String, String> channelParticipants = participants.getOrDefault(channelId, Map.of());
        if (channelParticipants.isEmpty()) {
            return List.of();
        }

        // 1. submitted=false 인 사람들만 이름 수집
        List<ParticipantStatusRes> statuses =
                participantService.getParticipantStatusByVoteId(voteId);

        Set<String> nonSubmittedNames = statuses.stream()
                .filter(s -> !Boolean.TRUE.equals(s.submitted()))
                .map(ParticipantStatusRes::displayName)
                .collect(Collectors.toSet());

        // 2. 디스코드 참석자 중 submitted=false 인 사람만 discordId 반환
        List<String> nonVoters = new ArrayList<>();
        for (Map.Entry<String, String> entry : channelParticipants.entrySet()) {
            if (nonSubmittedNames.contains(entry.getValue())) {
                nonVoters.add(entry.getKey());
            }
        }

        return nonVoters;
    }
    
    @Override
    public boolean hasPreviousVote(String channelId) {
        return hasVote.contains(channelId);
    }
    
    @Override
    public String recreateVote(String channelId, String channelName, int weeks) {
        // 이전 voteId 사용하지 않고,
        // 채널에 저장된 디스코드 참석자 목록을 기준으로 새 투표를 생성
        channelVoteId.remove(channelId);

        String shareUrl = createVote(channelId, channelName, weeks);
        System.out.println("[When:D] Vote recreated for channel " + channelId + " (weeks=" + weeks + ")");
        return shareUrl;
    }

    @Override
    public String getShareUrl(String channelId) {
        return channelShareUrl.get(channelId);
    }

    @Override
    public String getVoteDeadline(String channelId) {
        LocalDateTime createdAt = voteCreatedAt.get(channelId);
        if (createdAt == null) {
            return "No vote created.";
        }
        LocalDateTime deadline = createdAt.plusHours(24);

        return deadline.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    @Override
    public String getTopRankedDateTime(String channelId) {
        Long voteId = channelVoteId.get(channelId);
        if (voteId == null) {
            return "1순위 일정";
        }

        VoteResultRes res = voteResultService.getVoteResult(voteId);
        if (res == null || res.rankings() == null || res.rankings().isEmpty()) {
            return "1순위 일정";
        }

        // 1순위 찾기
        RankingRes top = res.rankings().stream()
                .filter(r -> r.rank() != null && r.rank() == 1)
                .findFirst()
                .orElse(res.rankings().get(0));

        LocalDate date = top.date();
        String period = top.period(); // LUNCH or DINNER

        String dayLabel = switch (date.getDayOfWeek()) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };

        String periodLabel = "LUNCH".equals(period) ? "점심" : "저녁";

        return date.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd"))
                + "(" + dayLabel + ") "
                + periodLabel;
    }

    @Override
    public String getChannelIdByVoteId(Long voteId) {
        for (Map.Entry<String, Long> entry : channelVoteId.entrySet()) {
            if (entry.getValue().equals(voteId)) {
                return entry.getKey();
            }
        }
        return null;
    }

}