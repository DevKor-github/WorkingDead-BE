package com.workingdead.chatbot.kakao.scheduler;

import com.workingdead.meet.entity.NotificationType;
import com.workingdead.meet.entity.Vote;
import com.workingdead.meet.entity.Vote.VoteStatus;
import com.workingdead.meet.repository.VoteRepository;
import com.workingdead.chatbot.kakao.service.KakaoNotifier;
import com.workingdead.meet.service.VoteNotificationService;
import com.workingdead.meet.service.VoteStatsService;
import com.workingdead.meet.service.VoteStatsService.VoteStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 카카오 챗봇 투표 알림 스케줄러 (DB 기반)
 *
 * - In-memory ScheduledFuture 사용 ❌
 * - 매 1분마다 ACTIVE 투표를 조회하여
 *   PRD 기준(집계 / 독촉 / 최후통첩 / 완료)을 판단
 * - vote_notification 테이블로 중복 발송 방지
 */
@Component
@Slf4j
public class KakaoWendyScheduler {

    private final VoteRepository voteRepository;
    private final KakaoNotifier notifier;
    private final VoteNotificationService notificationService;
    private final VoteStatsService voteStatsService;

    public KakaoWendyScheduler(
            VoteRepository voteRepository,
            KakaoNotifier notifier,
            VoteNotificationService notificationService,
            VoteStatsService voteStatsService
    ) {
        this.voteRepository = voteRepository;
        this.notifier = notifier;
        this.notificationService = notificationService;
        this.voteStatsService = voteStatsService;
    }

    /**
     * 1분 주기로 ACTIVE 투표를 스캔
     */
    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        List<Vote> activeVotes = voteRepository.findAllByStatus(VoteStatus.ACTIVE);
        for (Vote vote : activeVotes) {
            try {
                processVote(vote);
            } catch (Exception e) {
                log.error("[Kakao Scheduler] Failed to process voteId={}", vote.getId(), e);
            }
        }
    }

    private void processVote(Vote vote) {
        Instant now = Instant.now();
        Duration elapsed = Duration.between(vote.getCreatedAt(), now);

        VoteStats stats = voteStatsService.getStats(vote.getId());

        // === 1. 모든 인원이 투표 완료 (PRD 2.5) ===
        if (stats.allVoted()) {
            if (notificationService.markSentIfFirst(vote.getId(), NotificationType.DONE_ALL_VOTED)) {
                notifier.sendAllVoted(vote);
                vote.close();
                log.info("[Kakao Scheduler] All voted. voteId={} closed", vote.getId());
            }
            return;
        }

        // === 2. 결과 집계 (PRD 2.2)
        // 기본: 3분 경과 후
        // 예외: 3분 전이라도 과반 달성 시
        boolean shouldAggregate = elapsed.toMinutes() >= 3
                || (elapsed.toMinutes() < 3 && stats.majorityReached());

        if (shouldAggregate) {
            if (notificationService.markSentIfFirst(vote.getId(), NotificationType.AGGREGATION_3M)) {
                notifier.shareVoteStatus(vote);
            }
        }

        // === 3. 독촉 (PRD 2.3) ===
        if (stats.hasNonVoters()) {
            checkAndNudge(vote, elapsed, 30, NotificationType.NUDGE_30M);
            checkAndNudge(vote, elapsed, 120, NotificationType.NUDGE_2H);
            checkAndNudge(vote, elapsed, 360, NotificationType.NUDGE_6H);
            checkAndNudge(vote, elapsed, 720, NotificationType.NUDGE_12H);
        }

        // === 4. 최후통첩 (PRD 2.4) ===
        if (elapsed.toHours() >= 24 && stats.hasNonVoters()) {
            if (notificationService.markSentIfFirst(vote.getId(), NotificationType.ULTIMATUM_24H)) {
                notifier.sendFinalNotice(vote);
            }
        }
    }

    private void checkAndNudge(Vote vote, Duration elapsed, long minutes, NotificationType type) {
        if (elapsed.toMinutes() >= minutes) {
            if (notificationService.markSentIfFirst(vote.getId(), type)) {
                notifier.remindNonVoters(vote, type);
            }
        }
    }
}