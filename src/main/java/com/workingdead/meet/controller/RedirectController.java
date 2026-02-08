package com.workingdead.meet.controller;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.workingdead.meet.entity.Vote;
import com.workingdead.meet.service.VoteService;
import java.net.URI;

import java.net.URLEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final VoteService voteService;

    @org.springframework.beans.factory.annotation.Value("${whend.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * 카카오 챗봇 버튼 클릭 시 호출되는 리다이렉트 엔드포인트
     * - botGroupKey(채팅방 ID)를 기준으로 활성 투표를 조회
     * - 실제 투표 웹 페이지로 302 Redirect 수행
     */
    @GetMapping("/open-vote")
    public ResponseEntity<Void> openVote(
            @RequestParam String botGroupKey,
            @RequestParam String botUserKey
    ) {
        // 1) 현재 ACTIVE 투표 찾기
        Vote vote = voteService.findActiveVoteByBotGroupKey(botGroupKey);

        // 2) 웹으로 redirect (web 라우트: /{shareCode})
        URI target = URI.create(frontendBaseUrl + "/" + vote.getCode()
                + "?botUserKey=" + URLEncoder.encode(botUserKey, UTF_8)
        );
        return ResponseEntity.status(302).location(target).build();
    }
}
