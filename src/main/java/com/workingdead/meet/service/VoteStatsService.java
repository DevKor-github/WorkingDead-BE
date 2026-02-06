package com.workingdead.meet.service;

import com.workingdead.meet.dto.ParticipantDtos.ParticipantStatusRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 투표 통계(집계/독촉 판단용) 계산 서비스
 *
 * - Participant 프리생성(모수 확정) 이후의 PRD 흐름을 위해
 *   total/submitted/nonVoted/majorityReached 등을 계산합니다.
 */
@Service
@RequiredArgsConstructor
public class VoteStatsService {

    private final ParticipantService participantService;

    /**
     * 계산 결과
     */
    public record VoteStats(
            long total,
            long submitted,
            long nonVoted,
            boolean majorityReached
    ) {
        public long majorityThreshold() {
            // 과반: floor(total/2) + 1
            return (total / 2) + 1;
        }

        public boolean hasParticipants() {
            return total > 0;
        }

        public boolean allVoted() {
            return total > 0 && nonVoted == 0;
        }

        public boolean hasNonVoters() {
            return nonVoted > 0;
        }
    }

    /**
     * voteId 기준 통계 계산
     */
    public VoteStats getStats(Long voteId) {
        List<ParticipantStatusRes> statuses = participantService.getParticipantStatusByVoteId(voteId);

        long total = statuses.size();
        long submitted = statuses.stream()
                .filter(s -> Boolean.TRUE.equals(s.submitted()))
                .count();

        long nonVoted = total - submitted;
        long majority = (total / 2) + 1;
        boolean majorityReached = total > 0 && submitted >= majority;

        return new VoteStats(total, submitted, nonVoted, majorityReached);
    }
}
