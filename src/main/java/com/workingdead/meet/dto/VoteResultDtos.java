package com.workingdead.meet.dto;

import java.time.LocalDate;
import java.util.List;

public class VoteResultDtos {
    
    // 전체 결과 응답
    public record VoteResultRes(
            Long voteId,
            String voteName,
            List<RankingRes> rankings  // 3순위까지
    ) {}
    
    // 순위별 정보
    public record RankingRes(
            int rank,                    // 순위 (1, 2, 3)
            LocalDate date,              // 날짜
            String period,               // "LUNCH" or "DINNER"
            int voteCount,               // 투표 인원 수
            double priorityScore,        // 우선순위 가중치 합계
            List<VoterDetailRes> voters  // 투표자 상세 (드롭다운용)
    ) {}
    
    // 투표자 상세 정보
    public record VoterDetailRes(
            Long participantId,
            String participantName,
            Integer priorityIndex,       // 우선순위 (1, 2, 3) - 없으면 null
            Double weight               // 가중치 - 없으면 null
    ) {}
}