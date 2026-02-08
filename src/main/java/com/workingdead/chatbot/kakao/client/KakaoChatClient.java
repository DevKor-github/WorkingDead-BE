package com.workingdead.chatbot.kakao.client;

import java.util.List;

public interface KakaoChatClient {
    List<KakaoChatUser> fetchChatUsers(String botGroupKey);
}
