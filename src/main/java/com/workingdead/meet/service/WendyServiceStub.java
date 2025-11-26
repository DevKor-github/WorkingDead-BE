package com.workingdead.meet.service;

import com.workingdead.chatbot.dto.VoteResult;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WendyService 임시 구현체 (Stub)
 * 
 * ⚠️ 이 클래스는 테스트용 껍데기입니다!
 * 실제 로직 구현 후 이 클래스를 대체하거나 수정해주세요.
 * 
 * [구현 필요 사항]
 * 1. createVote() - 실제 투표 생성 API 연동 (VoteService 활용)
 * 2. getVoteStatus() - 투표 현황 조회 (VoteResultService 활용)
 * 3. getNonVoterIds() - 참석자 중 미투표자 조회
 * 4. hasNewVoter() - 마지막 체크 이후 신규 투표자 감지
 * 
 * [참고]
 * - 이 인터페이스는 WendyCommand, WendyScheduler에서 호출됩니다
 * - channelId = 디스코드 채널 ID (세션 구분용)
 */
@Service
public class WendyServiceStub implements WendyService {
    
    // 활성 세션 관리 (channelId 기반)
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    
    // 참석자 관리 (channelId -> memberId Set)
    private final Map<String, Set<String>> participants = new ConcurrentHashMap<>();
    
    // 투표 생성 여부 (재투표 체크용)
    private final Set<String> hasVote = ConcurrentHashMap.newKeySet();
    
    @Override
    public void startSession(String channelId, List<Member> members) {
        activeSessions.add(channelId);
        participants.put(channelId, ConcurrentHashMap.newKeySet());
        System.out.println("[Stub] Session started: " + channelId);
    }
    
    @Override
    public boolean isSessionActive(String channelId) {
        return activeSessions.contains(channelId);
    }
    
    @Override
    public void endSession(String channelId) {
        activeSessions.remove(channelId);
        participants.remove(channelId);
        hasVote.remove(channelId);
        System.out.println("[Stub] Session ended: " + channelId);
    }
    
    @Override
    public void addParticipant(String channelId, String memberId, String memberName) {
        participants.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(memberId);
        System.out.println("[Stub] Participant added: " + memberName + " (ID: " + memberId + ")");
    }
    
    @Override
    public String createVote(String channelId, int weeks) {
        hasVote.add(channelId);
        System.out.println("[Stub] Vote created: " + weeks + " weeks later");
        
        // TODO: 실제 구현 시 VoteService.createVote() 호출
        // 참고: participants.get(channelId)로 참석자 목록 조회 가능
        return "https://your-domain.com/vote/sample-id";
    }
    
    @Override
    public VoteResult getVoteStatus(String channelId) {
        // TODO: 실제 구현 필요
        // VoteResultService에서 투표 현황 조회 후 VoteResult로 변환
        // 반환값 예시:
        // - voteUrl: 투표 링크
        // - rankings: 1순위, 2순위, 3순위 날짜 + 투표자 목록
        return null;
    }
    
    @Override
    public boolean hasNewVoter(String channelId) {
        // TODO: 실제 구현 필요
        // 마지막 체크 시점 이후 새 투표자가 있으면 true
        // 구현 힌트: lastCheckTime 저장 후 비교
        return false;
    }
    
    @Override
    public List<String> getNonVoterIds(String channelId) {
        // TODO: 실제 구현 필요
        // 참석자(participants) - 투표자 = 미투표자
        // 반환값: 디스코드 멤버 ID 리스트 (멘션용)
        return List.of();
    }
    
    @Override
    public boolean hasPreviousVote(String channelId) {
        return hasVote.contains(channelId);
    }
    
    @Override
    public String recreateVote(String channelId, int weeks) {
        System.out.println("[Stub] Vote recreated: " + weeks + " weeks");
        // TODO: 기존 참석자로 새 투표 생성
        return "https://your-domain.com/vote/new-id";
    }
}