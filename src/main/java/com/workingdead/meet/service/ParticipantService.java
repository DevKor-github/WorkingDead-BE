package com.workingdead.meet.service;

import com.workingdead.meet.dto.*;
import com.workingdead.meet.entity.*;
import com.workingdead.meet.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.security.SecureRandom;
import java.util.NoSuchElementException;


@Service
@Transactional
public class ParticipantService {
    private final ParticipantRepository participantRepo;
    private final VoteRepository voteRepo;
    private static final String CODE_ALPHABET = "abcdefghijkmnopqrstuvwxyz23456789";
    private final SecureRandom rnd = new SecureRandom();


    public ParticipantService(ParticipantRepository participantRepo, VoteRepository voteRepo) {
        this.participantRepo = participantRepo; this.voteRepo = voteRepo;
    }


    public ParticipantDtos.ParticipantRes add(Long voteId, String displayName) {
        Vote v = voteRepo.findById(voteId).orElseThrow(() -> new NoSuchElementException("vote not found"));
        String loginCode = genCode(10);
        Participant p = new Participant(v, displayName);
        participantRepo.save(p);
        return new ParticipantDtos.ParticipantRes(p.getId(), p.getDisplayName());
    }


    public void remove(Long participantId) {
        participantRepo.deleteById(participantId);
    }


    private String genCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i=0;i<len;i++) sb.append(CODE_ALPHABET.charAt(rnd.nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }
}
