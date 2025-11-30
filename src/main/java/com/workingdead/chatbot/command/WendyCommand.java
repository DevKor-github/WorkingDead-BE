package com.workingdead.chatbot.command;

import com.workingdead.chatbot.scheduler.WendyScheduler;
import com.workingdead.chatbot.service.WendyService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
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

    private static final String ATTENDEE_SELECT_MENU_ID = "wendy-attendees";
    private static final String WEEK_SELECT_MENU_ID = "wendy-weeks";
    private static final String WEEK_SELECT_MENU_REVOTE_ID = "wendy-weeks-revote";
    
    public WendyCommand(WendyService wendyService, WendyScheduler wendyScheduler) {
        this.wendyService = wendyService;
        this.wendyScheduler = wendyScheduler;
    }

    @Override
    public void onGuildJoin(net.dv8tion.jda.api.events.guild.GuildJoinEvent event) {
        TextChannel defaultChannel = event.getGuild().getDefaultChannel().asTextChannel();
        if (defaultChannel != null) {
            defaultChannel.sendMessage("""
                안녕하세요! 일정 조율 도우미 웬디가 서버에 합류했어요 :D
                일정을 조율하려면 채팅에 **'웬디 시작'** 이라고 입력해 주세요!
                """).queue();
        }
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
        
//        // 2.1~2.2 날짜 범위 입력
//        if (waitingForDateInput.getOrDefault(channelId, false)) {
//            Integer weeks = extractWeeks(content);
//            if (weeks != null) {
//                handleDateInput(channel, member, weeks, false);
//                return;
//            }
//        }
        
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
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (!ATTENDEE_SELECT_MENU_ID.equals(event.getComponentId())) {
            return;
        }

        String channelId = event.getChannel().getId();
        if (!wendyService.isSessionActive(channelId)) {
            return;
        }

        event.getMentions().getMembers().forEach(member -> {
            wendyService.addParticipant(channelId, member.getId(), member.getEffectiveName());
            System.out.println("[Command] Participant added via select menu: " + member.getEffectiveName());
        });

        event.reply("참석자 명단이 업데이트됐어요!").setEphemeral(true).queue();

    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!WEEK_SELECT_MENU_ID.equals(componentId) && !WEEK_SELECT_MENU_REVOTE_ID.equals(componentId)) {
            return;
        }

        String channelId = event.getChannel().getId();
        if (!wendyService.isSessionActive(channelId)) {
            return;
        }

        // 하나만 선택하게 설정할 예정이므로 첫 번째 값만 사용
        String value = event.getValues().get(0);
        int weeks;
        try {
            weeks = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            event.reply("선택한 값이 올바르지 않아요. 다시 시도해주세요!").setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        Member member = event.getMember();
        if (member == null) {
            event.reply("사용자 정보를 가져올 수 없어요. 다시 시도해주세요!").setEphemeral(true).queue();
            return;
        }

        boolean isRevote = WEEK_SELECT_MENU_REVOTE_ID.equals(componentId);
        handleDateInput(channel, member, weeks, isRevote);
        event.reply("투표 날짜 범위를 선택하셨어요!").setEphemeral(true).queue();
    }

//    @Override
//    public void onMessageReactionAdd(MessageReactionAddEvent event) {
//        if (event.getUser() != null && event.getUser().isBot()) return;
//
//        String channelId = event.getChannel().getId();
//        String messageId = event.getMessageId();
//
//        String checkMessageId = participantCheckMessages.get(channelId);
//        if (checkMessageId == null || !checkMessageId.equals(messageId)) {
//            return;
//        }
//
//        if (!event.getReaction().getEmoji().equals(Emoji.fromUnicode("✅"))) {
//            return;
//        }
//
//        event.retrieveMember().queue(member -> {
//            if (member != null) {
//                wendyService.addParticipant(channelId, member.getId(), member.getEffectiveName());
//                System.out.println("[Command] Participant added: " + member.getEffectiveName());
//            }
//        });
//    }
    
    private void handleStart(TextChannel channel) {
        String channelId = channel.getId();
        List<Member> members = channel.getMembers();
        
        wendyService.startSession(channelId, members);
        
        channel.sendMessage("""
            안녕하세요! 일정 조율 도우미 웬디에요 :D
            지금부터 여러분의 일정 조율을 도와드릴게요
            """).queue();


        // 참석자 입력용 엔티티 셀렉트 메뉴 (유저 선택 드롭다운)
        EntitySelectMenu attendeeMenu = EntitySelectMenu.create(ATTENDEE_SELECT_MENU_ID, EntitySelectMenu.SelectTarget.USER)
                .setPlaceholder("참석자를 선택 / 검색해 주세요.")
                .setRequiredRange(1, 25)
                .build();

        channel.sendMessage("인원 파악을 위해 참석자분들을 알려주세요!\n원하는 참석자들을 아래 드롭다운에서 선택해주세요.")
                .setActionRow(attendeeMenu)
                .queue();

        // 2.1 날짜 범위 파악 질문 (드롭다운 방식)
        StringSelectMenu weekMenu = StringSelectMenu.create(WEEK_SELECT_MENU_ID)
                .setPlaceholder("몇 주 뒤의 일정을 계획하시나요?")
                .addOption("이번 주", "0")
                .addOption("1주 뒤", "1")
                .addOption("2주 뒤", "2")
                .addOption("3주 뒤", "3")
                .addOption("4주 뒤", "4")
                .addOption("5주 뒤", "5")
                .addOption("6주 뒤", "6")
                .build();

        channel.sendMessage("몇 주 뒤의 일정을 계획하시나요? :D")
                .setActionRow(weekMenu)
                .queue();
    }
    
    private void handleDateInput(TextChannel channel, Member member, int weeks, boolean isRevote) {
        String channelId = channel.getId();
        String userMention = member.getAsMention();
        String channelName = channel.getName();
        
        waitingForDateInput.put(channelId, false);
        
        channel.sendMessage(userMention + " 님이 " + weeks + "주 뒤를 선택하셨어요!").queue();
        channel.sendMessage("해당 일정의 투표를 만들어드릴게요 :D").queue();
        channel.sendMessage("(투표 늦게 하는 사람 대머리🧑‍🦲)").queue();
        channel.sendMessage("투표를 생성 중입니다🛜").queue();
        
        String voteUrl = isRevote 
            ? wendyService.recreateVote(channelId, channelName, weeks)
            : wendyService.createVote(channelId, channelName, weeks);
        
        channel.sendMessage(voteUrl).queue();
        wendyScheduler.startSchedule(channel);


//        // 투표 제한시간(24시간) + 30분 후 자동 종료 스케줄
//        CompletableFuture
//                .delayedExecutor(3 * 60 + 30, TimeUnit.SECONDS)
//                .execute(() -> {
//                    String chId = channel.getId();
//                    // 스케줄러 정리 + 세션 종료
//                    wendyScheduler.stopSchedule(chId);
//                    wendyService.endSession(chId);
//
//                    // 안내 메시지 전송
//                    channel.sendMessage("""
//                        투표 제한 시간이 지나 웬디가 자동으로 종료되었어요 :D
//                        다시 일정 조율이 필요하시면 '웬디 시작'을 입력해 주세요!
//                        """).queue();
//                });
    }
    
    private void handleRevote(TextChannel channel) {
        String channelId = channel.getId();
        
        if (!wendyService.hasPreviousVote(channelId)) {
            channel.sendMessage("아직 진행된 투표가 없어요🗑️").queue();
            return;
        }
        
        wendyScheduler.stopSchedule(channelId);


        StringSelectMenu weekMenu = StringSelectMenu.create(WEEK_SELECT_MENU_REVOTE_ID)
                .setPlaceholder("몇 주 뒤의 일정을 다시 계획하시나요?")
                .addOption("이번 주", "0")
                .addOption("1주 뒤", "1")
                .addOption("2주 뒤", "2")
                .addOption("3주 뒤", "3")
                .addOption("4주 뒤", "4")
                .addOption("5주 뒤", "5")
                .addOption("6주 뒤", "6")
                .build();

        channel.sendMessage("몇 주 뒤의 일정을 계획하시나요? :D")
                .setActionRow(weekMenu)
                .queue();
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