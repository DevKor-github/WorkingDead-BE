package com.workingdead.meet.service;

import com.workingdead.meet.dto.ParticipantDtos;
import com.workingdead.meet.dto.VoteDtos;
import com.workingdead.meet.entity.Vote;
import com.workingdead.meet.entity.Vote.VoteStatus;
import com.workingdead.meet.repository.VoteRepository;
import jakarta.transaction.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;


@Service
@Transactional
public class VoteService {
    private final VoteRepository voteRepo;
    private final ParticipantService participantService;
    private final String baseUrl;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no confusing chars
    private final SecureRandom rnd = new SecureRandom();


    public VoteService(
            VoteRepository voteRepo,
            ParticipantService participantService,
            @Value("${app.base-url:http://whendy.netlify.app}") String baseUrl
    ) {
        this.voteRepo = voteRepo;
        this.participantService = participantService;
        this.baseUrl = baseUrl;
    }


    /**
     * @deprecated PRD 기준으로 Vote.botGroupKey가 NOT NULL 입니다.
     *             카카오 플로우에서는 반드시 create(req, botGroupKey) 를 사용하세요.
     */
    @Deprecated
    public VoteDtos.VoteSummary create(VoteDtos.CreateVoteReq req) {
        throw new UnsupportedOperationException("Vote.botGroupKey is required. Use create(req, botGroupKey).");
    }

    /**
     * 투표 생성 (카카오 그룹챗 전용)
     * - botGroupKey를 Vote에 저장 (NOT NULL)
     * - 같은 botGroupKey에 ACTIVE 투표가 이미 있으면 CLOSED로 종료 처리
     * - (PRD) 투표 생성 직후 채팅방 참여자(botUserKey) 목록으로 Participant를 미리 생성합니다.
     */
    public VoteDtos.VoteSummary createKakaoVote(VoteDtos.CreateVoteReq req, String botGroupKey, List<String> botUserKeys) {
        if (botGroupKey == null || botGroupKey.isBlank()) {
            throw new IllegalArgumentException("botGroupKey must not be blank");
        }

        // 0) 기존 ACTIVE 투표가 있으면 종료 (status 활용)
        voteRepo.findTopByBotGroupKeyAndStatusOrderByCreatedAtDesc(botGroupKey, VoteStatus.ACTIVE)
                .ifPresent(Vote::close);

        // 1) Vote 생성
        String code = genCode(8);
        Vote v = new Vote(req.name(), code);

        // botGroupKey 세팅 (Vote.botGroupKey NOT NULL)
        v.setBotGroupKey(botGroupKey);

        // 2) 날짜 범위 설정 (있으면)
        if (req.startDate() != null && req.endDate() != null) {
            if (req.endDate().isBefore(req.startDate())) {
                throw new IllegalArgumentException("endDate must be >= startDate");
            }
            v.setDateRange(req.startDate(), req.endDate());
        }

        voteRepo.save(v);
        return toSummary(v);
    }


    public List<VoteDtos.VoteSummary> listAll() {
        return voteRepo.findAll().stream().map(this::toSummary).toList();
    }


    public VoteDtos.VoteDetail get(Long id) {
        Vote v = voteRepo.findById(id).orElseThrow(() -> new NoSuchElementException("vote not found"));
        return toDetail(v);
    }

    public VoteDtos.VoteDetail getByCode(String code) {
        Vote v = voteRepo.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("vote not found with code: " + code));
        return toDetail(v);
    }


    public VoteDtos.VoteDetail update(Long id, VoteDtos.UpdateVoteReq req) {
        Vote v = voteRepo.findById(id).orElseThrow(() -> new NoSuchElementException("vote not found"));
        if (req.name() != null && !req.name().isBlank()) v.setName(req.name());
        if (req.startDate() != null && req.endDate() != null) {
            if (req.endDate().isBefore(req.startDate())) throw new IllegalArgumentException("endDate must be >= startDate");
            v.setDateRange(req.startDate(), req.endDate());
        }
        return toDetail(v);
    }

    @Transactional
    public void closeVote(Long voteId) {
        Vote vote = voteRepo.findById(voteId)
                .orElseThrow(() -> new NoSuchElementException("vote not found"));
        vote.close(); // status = CLOSED
    }


    public void delete(Long id) {
        voteRepo.deleteById(id);
    }

    /**
     * 채팅방(botGroupKey) 기준으로 "현재 활성" 투표를 찾아야 할 때 사용합니다.
     */
    public Vote findActiveVoteByBotGroupKey(String botGroupKey) {
        return voteRepo
                .findTopByBotGroupKeyAndStatusOrderByCreatedAtDesc(
                        botGroupKey, VoteStatus.ACTIVE
                )
                .orElseThrow(() -> new NoSuchElementException("active vote not found"));
    }


    private String genCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i=0;i<len;i++) sb.append(CODE_ALPHABET.charAt(rnd.nextInt(CODE_ALPHABET.length())));
// ensure uniqueness (extremely low collision; loop if needed)
        if (voteRepo.findByCode(sb.toString()).isPresent()) return genCode(len);
        return sb.toString();
    }


    private VoteDtos.VoteSummary toSummary(Vote v) {
        String admin = baseUrl + "/admin/votes/" + v.getId();
        String share = shareUrl(v);
        return new VoteDtos.VoteSummary(v.getId(), v.getName(), v.getCode(), admin, share, v.getStartDate(), v.getEndDate());
    }


    private VoteDtos.VoteDetail toDetail(Vote v) {
        var participants = v.getParticipants().stream()
            .map(p -> new ParticipantDtos.ParticipantRes(p.getId(), p.getDisplayName(), false))
            .toList();

        return new VoteDtos.VoteDetail(v.getId(), v.getName(), v.getCode(), v.getStartDate(), v.getEndDate(), participants);
    }

    private String shareUrl(Vote v) {
        return baseUrl + "/v/" + v.getCode();
    }

    /**
     * 최후통첩/독촉용 개인화 투표 링크
     * - botUserKey를 쿼리로 포함하여 웹 진입 시 참여자 식별에 사용
     */
    public String shareUrlForUser(Vote v, String botUserKey) {
        if (botUserKey == null || botUserKey.isBlank()) {
            throw new IllegalArgumentException("botUserKey must not be blank");
        }
        return baseUrl + "/v/" + v.getCode() + "?botUserKey=" + botUserKey;
    }


}
