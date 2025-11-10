package com.workingdead.meet.service;

import com.workingdead.meet.dto.ParticipantDtos;
import com.workingdead.meet.dto.VoteDtos;
import com.workingdead.meet.entity.Vote;
import com.workingdead.meet.repository.VoteRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;


@Service
@Transactional
public class VoteService {
    private final VoteRepository voteRepo;
    private final String baseUrl;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no confusing chars
    private final SecureRandom rnd = new SecureRandom();


    public VoteService(VoteRepository voteRepo, @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.voteRepo = voteRepo; this.baseUrl = baseUrl;
    }


    public VoteDtos.VoteSummary create(String name) {
        String code = genCode(8);
        Vote v = new Vote(name, code);
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


    public VoteDtos.VoteDetail update(Long id, VoteDtos.UpdateVoteReq req) {
        Vote v = voteRepo.findById(id).orElseThrow(() -> new NoSuchElementException("vote not found"));
        if (req.name() != null && !req.name().isBlank()) v.setName(req.name());
        if (req.startDate() != null && req.endDate() != null) {
            if (req.endDate().isBefore(req.startDate())) throw new IllegalArgumentException("endDate must be >= startDate");
            v.setDateRange(req.startDate(), req.endDate());
        }
        return toDetail(v);
    }


    public void delete(Long id) {
        voteRepo.deleteById(id);
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
        String share = baseUrl + "/v/" + v.getCode();
        return new VoteDtos.VoteSummary(v.getId(), v.getName(), v.getCode(), admin, share, v.getStartDate(), v.getEndDate());
    }


    private VoteDtos.VoteDetail toDetail(Vote v) {
        var participants = v.getParticipants().stream().map(p -> new ParticipantDtos.ParticipantRes(p.getId(), p.getDisplayName())).toList();
        return new VoteDtos.VoteDetail(v.getId(), v.getName(), v.getCode(), v.getStartDate(), v.getEndDate(), participants);
    }
}
