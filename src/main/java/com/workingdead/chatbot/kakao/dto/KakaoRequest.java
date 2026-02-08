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
        private ChatProperties properties;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatProperties {
        private String botGroupKey;
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
        private Chat chat;
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
        private String botUserKey;
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
        private Map<String, Object> detailParams;
        private Map<String, Object> clientExtra;
    }

    // 편의 메서드
    public String getUserKey() {
        if (userRequest == null || userRequest.getUser() == null) return null;

        Properties p = userRequest.getUser().getProperties();
        if (p != null) {
            String plusfriendUserKey = p.getPlusfriendUserKey();
            if (plusfriendUserKey != null && !plusfriendUserKey.isBlank()) {
                return plusfriendUserKey;
            }
        }
        String id = userRequest.getUser().getId();
        return (id == null || id.isBlank()) ? null : id;
    }

    public String getUtterance() {
        if (userRequest != null) {
            return userRequest.getUtterance();
        }
        return null;
    }

    public String getParam(String key) {
        if (key == null || key.isBlank()) return null;

        // 1) action.params 우선
        if (action != null && action.getParams() != null) {
            String v = action.getParams().get(key);
            if (v != null) return v;
        }

        // 2) userRequest.params fallback (오픈빌더 설정에 따라 여기로 들어오는 케이스 대비)
        if (userRequest != null && userRequest.getParams() != null) {
            Object v = userRequest.getParams().get(key);
            return v == null ? null : String.valueOf(v);
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
     * - 카카오 요청에서는 userRequest.chat.id 가 botGroupKey로 사용 가능하며,
     *   userRequest.chat.properties.botGroupKey 로도 전달됩니다.
     */
    public String getBotGroupKey() {
        // 1) userRequest.chat.id (요청에 기본 포함)
        if (userRequest != null && userRequest.getChat() != null) {
            String id = userRequest.getChat().getId();
            if (id != null && !id.isBlank()) return id;

            // 2) userRequest.chat.properties.botGroupKey
            if (userRequest.getChat().getProperties() != null) {
                String key = userRequest.getChat().getProperties().getBotGroupKey();
                if (key != null && !key.isBlank()) return key;
            }
        }

        // 3) 호환: 최상위 chat.id (현재 JSON에는 없지만 혹시 몰라 유지)
        if (chat != null) {
            String id = chat.getId();
            if (id != null && !id.isBlank()) return id;
        }
        return null;
    }

    /**
     * 그룹 채팅방 여부 확인
     */
    public boolean isGroupChat() {
        String key = getBotGroupKey();
        return key != null && !key.isBlank();
    }

    /**
     * 그룹챗 사용자 식별용 botUserKey
     * - 카카오 요청에서는 userRequest.user.properties.botUserKey 로 전달됩니다.
     * - fallback: userRequest.user.id
     */
    public String getBotUserKey() {
        if (userRequest != null && userRequest.getUser() != null) {
            Properties p = userRequest.getUser().getProperties();
            if (p != null) {
                String key = p.getBotUserKey();
                if (key != null && !key.isBlank()) return key;
            }
            String id = userRequest.getUser().getId();
            return (id == null || id.isBlank()) ? null : id;
        }
        return null;
    }
}