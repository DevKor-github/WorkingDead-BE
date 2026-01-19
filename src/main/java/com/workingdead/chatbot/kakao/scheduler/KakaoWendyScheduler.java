package com.workingdead.chatbot.kakao.scheduler;

import com.workingdead.chatbot.kakao.service.KakaoNotifier;
import com.workingdead.chatbot.kakao.service.KakaoNotifier.RemindTiming;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 카카오 챗봇용 스케줄러
 *
 * Discord WendyScheduler와 동일한 타이밍으로 알림을 전송합니다.
 * userKey 기반으로 스케줄을 관리합니다.
 */
@Component
@Slf4j
public class KakaoWendyScheduler {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final KakaoNotifier notifier;
    private final Map<String, List<ScheduledFuture<?>>> userTasks = new ConcurrentHashMap<>();

    public KakaoWendyScheduler(KakaoNotifier notifier) {
        this.notifier = notifier;
    }

    /**
     * 스케줄 시작 (투표 생성 후 호출)
     */
    public void startSchedule(String userKey) {
        stopSchedule(userKey);

        CopyOnWriteArrayList<ScheduledFuture<?>> tasks = new CopyOnWriteArrayList<>();

        // 투표 현황: 10분 후 첫 공유
        tasks.add(scheduler.schedule(
                () -> notifier.shareVoteStatus(userKey),
                10, TimeUnit.MINUTES
        ));

        // 미투표자 독촉
        tasks.add(scheduler.schedule(
                () -> notifier.remindNonVoters(userKey, RemindTiming.MIN_15),
                15, TimeUnit.MINUTES
        ));
        tasks.add(scheduler.schedule(
                () -> notifier.remindNonVoters(userKey, RemindTiming.HOUR_1),
                1, TimeUnit.HOURS
        ));
        tasks.add(scheduler.schedule(
                () -> notifier.remindNonVoters(userKey, RemindTiming.HOUR_6),
                6, TimeUnit.HOURS
        ));
        tasks.add(scheduler.schedule(
                () -> notifier.remindNonVoters(userKey, RemindTiming.HOUR_12),
                12, TimeUnit.HOURS
        ));
        tasks.add(scheduler.schedule(
                () -> notifier.remindNonVoters(userKey, RemindTiming.HOUR_24),
                24, TimeUnit.HOURS
        ));

        userTasks.put(userKey, tasks);
        log.info("[Kakao Scheduler] Schedule started: {}", userKey);
    }

    /**
     * 스케줄 중지 (세션 종료 또는 재투표 시 호출)
     */
    public void stopSchedule(String userKey) {
        List<ScheduledFuture<?>> tasks = userTasks.remove(userKey);
        if (tasks != null) {
            tasks.forEach(task -> task.cancel(false));
            log.info("[Kakao Scheduler] Schedule stopped: {}", userKey);
        }
    }

    /**
     * 활성 스케줄 여부 확인
     */
    public boolean hasActiveSchedule(String userKey) {
        return userTasks.containsKey(userKey);
    }
}