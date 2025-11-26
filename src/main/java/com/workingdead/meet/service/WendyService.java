package com.workingdead.meet.service;

import com.workingdead.chatbot.dto.VoteResult;
import net.dv8tion.jda.api.entities.Member;

import java.util.List;

public interface WendyService {
    void startSession(String channelId, List<Member> participants);
    boolean isSessionActive(String channelId);
    void endSession(String channelId);
    
    void addParticipant(String channelId, String memberId, String memberName);
    
    String createVote(String channelId, int weeks);
    VoteResult getVoteStatus(String channelId);
    boolean hasNewVoter(String channelId);
    List<String> getNonVoterIds(String channelId);
    
    boolean hasPreviousVote(String channelId);
    String recreateVote(String channelId, int weeks);
}