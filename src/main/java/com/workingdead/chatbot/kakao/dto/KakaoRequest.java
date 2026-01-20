package com.workingdead.chatbot.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * 카카오 i 오픈빌더 스킬 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoRequest {

    private Intent intent;
    private UserRequest userRequest;
    private Bot bot;
    private Action action;
    private Chat chat;  // 그룹 채팅방 정보

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chat {
        private String id;      // 채팅방 ID (botGroupKey)
        private String type;    // 채팅방 타입
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Intent {
        private String id;
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserRequest {
        private String timezone;
        private Map<String, Object> params;
        private Block block;
        private String utterance;
        private String lang;
        private User user;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Block {
        private String id;
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        private String id;
        private String type;
        private Properties properties;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Properties {
        private String plusfriendUserKey;
        private String appUserId;
        private Boolean isFriend;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Bot {
        private String id;
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Action {
        private String name;
        private String id;
        private Map<String, String> params;
        private Map<String, String> detailParams;
        private Map<String, Object> clientExtra;
    }

    // 편의 메서드
    public String getUserKey() {
        if (userRequest != null && userRequest.getUser() != null) {
            return userRequest.getUser().getId();
        }
        return null;
    }

    public String getUtterance() {
        if (userRequest != null) {
            return userRequest.getUtterance();
        }
        return null;
    }

    public String getParam(String key) {
        if (action != null && action.getParams() != null) {
            return action.getParams().get(key);
        }
        return null;
    }

    public String getBotId() {
        if (bot != null) {
            return bot.getId();
        }
        return null;
    }

    /**
     * 그룹 채팅방 키 (botGroupKey) 조회
     */
    public String getBotGroupKey() {
        if (chat != null) {
            return chat.getId();
        }
        return null;
    }

    /**
     * 그룹 채팅방 여부 확인
     */
    public boolean isGroupChat() {
        return chat != null && chat.getId() != null;
    }

    /**
     * 사용자의 botUserKey 조회 (그룹챗에서 사용자 식별용)
     */
    public String getBotUserKey() {
        if (userRequest != null && userRequest.getUser() != null) {
            return userRequest.getUser().getId();
        }
        return null;
    }
}