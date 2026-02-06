package com.workingdead.chatbot.kakao.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class KakaoChatClientImpl implements KakaoChatClient {

    private final RestClient restClient;
    private final String botId;

    public KakaoChatClientImpl(
            RestClient.Builder builder,
            @Value("${kakao.bot.base-url}") String baseUrl,
            @Value("${kakao.bot.bot-id}") String botId,
            @Value("${kakao.rest.api-key}") String restApiKey
    ) {
        this.botId = botId;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    @Override
    public List<KakaoChatUser> fetchChatUsers(String botGroupKey) {
        KakaoChatMembersResponse res = restClient.get()
                .uri("/v2/bots/{botId}/group-chat-rooms/{botGroupKey}/members", botId, botGroupKey)
                .retrieve()
                .body(KakaoChatMembersResponse.class);

        if (res == null || res.users() == null) return List.of();

        return res.users().stream()
                .map(KakaoChatUser::new) // botUserKey 그대로
                .toList();
    }
}