package com.workingdead.chatbot.scheduler;

import com.workingdead.meet.dto.VoteResultDtos.RankingRes;
import com.workingdead.meet.dto.VoteResultDtos.VoteResultRes;
import com.workingdead.chatbot.service.WendyService;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class WendyScheduler {
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final WendyService wendyService;
    private final Map<String, List<ScheduledFuture<?>>> channelTasks = new ConcurrentHashMap<>();
    
    public WendyScheduler(WendyService wendyService) {
        this.wendyService = wendyService;
    }
    
    public void startSchedule(TextChannel channel) {
        String channelId = channel.getId();
        stopSchedule(channelId);
        
        CopyOnWriteArrayList<ScheduledFuture<?>> tasks = new CopyOnWriteArrayList<>();
        
        // 2.3 투표 현황: 10분 후 첫 공유
        tasks.add(scheduler.schedule(() -> shareVoteStatus(channel), 10, TimeUnit.MINUTES));

        // 2.4 미투표자 독촉
        tasks.add(scheduler.schedule(() -> remindNonVoters(channel, RemindTiming.MIN_15), 15, TimeUnit.MINUTES));
        tasks.add(scheduler.schedule(() -> remindNonVoters(channel, RemindTiming.HOUR_1), 1, TimeUnit.HOURS));
        tasks.add(scheduler.schedule(() -> remindNonVoters(channel, RemindTiming.HOUR_6), 6, TimeUnit.HOURS));
        tasks.add(scheduler.schedule(() -> remindNonVoters(channel, RemindTiming.HOUR_12), 12, TimeUnit.HOURS));
        tasks.add(scheduler.schedule(() -> remindNonVoters(channel, RemindTiming.HOUR_24), 24, TimeUnit.HOURS));
        
        channelTasks.put(channelId, tasks);
        System.out.println("[Scheduler] Schedule started: " + channelId);
    }
    
    private void shareVoteStatus(TextChannel channel) {
        try {
            VoteResultRes result = wendyService.getVoteStatus(channel.getId());
            String shareUrl = wendyService.getShareUrl(channel.getId());
            
            if (result == null || result.rankings() == null || result.rankings().isEmpty()) {
                channel.sendMessage("""
                    웬디가 투표 현황을 공유드려요! :D
                    
                    엥 아직 아무도 투표를 안 했네요 :(
                    
                    투표하러 가기: """ + shareUrl
                ).queue();
                return;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("웬디가 투표 현황을 공유드려요! :D\n");

            if (shareUrl != null && !shareUrl.isBlank()) {
                sb.append("\n투표하러 가기: ").append(shareUrl).append("\n\n");
            } else {
                sb.append("\n투표 링크가 준비되지 않았어요 😢\n\n");
            }
            
            for (RankingRes rank : result.rankings()) {
                if (rank.rank() == null) continue;

                String periodLabel = "LUNCH".equals(rank.period()) ? "점심" : "저녁";
                sb.append("📌")
                        .append(rank.rank()).append("순위 ")
                        .append(rank.date()).append(" ").append(periodLabel).append("\n");

                if (rank.voters() != null && !rank.voters().isEmpty()) {
                    String voterStr = rank.voters().stream()
                            .map(v -> v.participantName() +
                                    (v.priorityIndex() != null ? "(" + v.priorityIndex() + ")" : ""))
                            .collect(Collectors.joining(", "));
                    sb.append("투표자: ").append(voterStr).append("\n");
                }
                sb.append("\n");


            }
            
            channel.sendMessage(sb.toString()).queue();
        } catch (Exception e) {
            System.err.println("[Scheduler] Failed to share vote status: " + e.getMessage());
        }
    }
    
    private void checkNewVoterAndShare(TextChannel channel) {
        try {
            if (wendyService.hasNewVoter(channel.getId())) {
                shareVoteStatus(channel);
                System.out.println("[Scheduler] New voter detected: " + channel.getId());
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Failed to check new voter: " + e.getMessage());
        }
    }
    
    private void remindNonVoters(TextChannel channel, RemindTiming timing) {
        try {
            List<String> nonVoterIds = wendyService.getNonVoterIds(channel.getId());
            
            if (nonVoterIds == null || nonVoterIds.isEmpty()) {
                return;
            }
            
            String mentions = nonVoterIds.stream()
                .map(id -> "<@" + id + ">")
                .collect(Collectors.joining(" "));
            
            String message = switch (timing) {
                case MIN_15, HOUR_1 -> mentions + " 투표가 시작됐어요! 다른 분들을 위해 빠른 참여 부탁드려요 :D";
                case HOUR_6 -> "다들 " + mentions + " 님의 투표를 기다리고 있어요🙌";
                case HOUR_12 -> mentions + " 웬디 기다리다 지쳐버림…🥹 대머리신가요?";
                case HOUR_24 -> {
                    String bestDateTime = wendyService.getTopRankedDateTime(channel.getId());
                    String deadline = wendyService.getVoteDeadline(channel.getId());
                    yield "최후통첩✉️\n" + mentions + "\n\n: " + deadline + "까지 투표 불참 시, " + bestDateTime + "으로 확정됩니다";
                }
            };
            
            channel.sendMessage(message).queue();
            System.out.println("[Scheduler] Reminder sent: " + timing);
        } catch (Exception e) {
            System.err.println("[Scheduler] Failed to send reminder: " + e.getMessage());
        }
    }
    
    public void stopSchedule(String channelId) {
        List<ScheduledFuture<?>> tasks = channelTasks.remove(channelId);
        if (tasks != null) {
            tasks.forEach(task -> task.cancel(false));
            System.out.println("[Scheduler] Schedule stopped: " + channelId);
        }
    }


    public void triggerImmediateStatusShare(TextChannel channel) {
        shareVoteStatus(channel);
        System.out.println("[Scheduler] Immediate vote status share triggered: " + channel.getId());
    }



    private enum RemindTiming { MIN_15, HOUR_1, HOUR_6, HOUR_12, HOUR_24 }
}