package com.workingdead.chatbot.command;

import com.workingdead.chatbot.scheduler.WendyScheduler;
import com.workingdead.meet.service.WendyService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WendyCommand extends ListenerAdapter {
    
    private final WendyService wendyService;
    private final WendyScheduler wendyScheduler;
    
    private final Map<String, String> participantCheckMessages = new ConcurrentHashMap<>();
    private final Map<String, Boolean> waitingForDateInput = new ConcurrentHashMap<>();
    
    public WendyCommand(WendyService wendyService, WendyScheduler wendyScheduler) {
        this.wendyService = wendyService;
        this.wendyScheduler = wendyScheduler;
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        
        String content = event.getMessage().getContentRaw().trim();
        TextChannel channel = event.getChannel().asTextChannel();
        String channelId = channel.getId();
        Member member = event.getMember();
        
        // 1.1 웬디 시작
        if (content.equals("웬디 시작")) {
            handleStart(channel);
            return;
        }
        
        // 4.1 도움말
        if (content.equals("/help") || content.equals("웬디 도움말")) {
            handleHelp(channel);
            return;
        }
        
        // 세션 체크
        if (!wendyService.isSessionActive(channelId)) {
            return;
        }
        
        // 2.1~2.2 날짜 범위 입력
        if (waitingForDateInput.getOrDefault(channelId, false)) {
            Integer weeks = extractWeeks(content);
            if (weeks != null) {
                handleDateInput(channel, member, weeks, false);
                return;
            }
        }
        
        // 4.2 재투표
        if (content.equals("웬디 재투표")) {
            handleRevote(channel);
            return;
        }
        
        // 3.1 웬디 종료
        if (content.equals("웬디 종료")) {
            handleEnd(channel);
            return;
        }
    }
    
    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() != null && event.getUser().isBot()) return;
        
        String channelId = event.getChannel().getId();
        String messageId = event.getMessageId();
        
        String checkMessageId = participantCheckMessages.get(channelId);
        if (checkMessageId == null || !checkMessageId.equals(messageId)) {
            return;
        }
        
        if (!event.getReaction().getEmoji().equals(Emoji.fromUnicode("✅"))) {
            return;
        }
        
        event.retrieveMember().queue(member -> {
            if (member != null) {
                wendyService.addParticipant(channelId, member.getId(), member.getEffectiveName());
                System.out.println("[Command] Participant added: " + member.getEffectiveName());
            }
        });
    }
    
    private void handleStart(TextChannel channel) {
        String channelId = channel.getId();
        List<Member> members = channel.getMembers();
        
        wendyService.startSession(channelId, members);
        
        channel.sendMessage("""
            안녕하세요! 일정 조율 도우미 웬디에요 :D
            지금부터 여러분의 일정 조율을 도와드릴게요
            """).queue();
        
        channel.sendMessage("인원 파악을 위해 참석자분들은 ✅를 남겨주세요!")
            .queue(message -> {
                participantCheckMessages.put(channelId, message.getId());
                message.addReaction(Emoji.fromUnicode("✅")).queue();
                System.out.println("[Command] Session started: " + channelId);
            });
        
        channel.sendMessage("몇 주 뒤의 일정을 계획하시나요? :D\n(ex. 2주 뒤)").queue();
        waitingForDateInput.put(channelId, true);
    }
    
    private void handleDateInput(TextChannel channel, Member member, int weeks, boolean isRevote) {
        String channelId = channel.getId();
        String userName = member.getEffectiveName();
        
        waitingForDateInput.put(channelId, false);
        
        channel.sendMessage(userName + " 님이 " + weeks + "주 뒤를 선택하셨어요!").queue();
        channel.sendMessage("해당 일정의 투표를 만들어드릴게요 :D").queue();
        channel.sendMessage("(투표 늦게 하는 사람 대머리🧑‍🦲)").queue();
        channel.sendMessage("투표를 생성 중입니다🛜").queue();
        
        String voteUrl = isRevote 
            ? wendyService.recreateVote(channelId, weeks)
            : wendyService.createVote(channelId, weeks);
        
        channel.sendMessage(voteUrl).queue();
        wendyScheduler.startSchedule(channel);
    }
    
    private void handleRevote(TextChannel channel) {
        String channelId = channel.getId();
        
        if (!wendyService.hasPreviousVote(channelId)) {
            channel.sendMessage("아직 진행된 투표가 없어요🗑️").queue();
            return;
        }
        
        wendyScheduler.stopSchedule(channelId);
        channel.sendMessage("몇 주 뒤의 일정을 계획하시나요? :D\n(ex. 2주 뒤)").queue();
        waitingForDateInput.put(channelId, true);
    }
    
    private void handleEnd(TextChannel channel) {
        String channelId = channel.getId();
        
        wendyScheduler.stopSchedule(channelId);
        wendyService.endSession(channelId);
        
        participantCheckMessages.remove(channelId);
        waitingForDateInput.remove(channelId);
        
        channel.sendMessage("""
            웬디는 여기서 눈치껏 빠질게요 :D
            모두 알찬 시간 보내세요!
            """).queue();
        System.out.println("[Command] Session ended: " + channelId);
    }
    
    private void handleHelp(TextChannel channel) {
        channel.sendMessage("""
            웬디는 다음과 같은 기능이 있어요!
            
            **'웬디 시작'**: 일정 조율을 시작해요
            **'웬디 종료'**: 작동을 종료해요
            **'웬디 재투표'**: 동일한 참석자로 투표를 다시 올려요
            """).queue();
    }
    
    private Integer extractWeeks(String content) {
        String numbers = content.replaceAll("[^0-9]", "");
        if (numbers.isEmpty()) return null;
        try {
            int weeks = Integer.parseInt(numbers);
            if (weeks < 1 || weeks > 12) return null;
            return weeks;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}