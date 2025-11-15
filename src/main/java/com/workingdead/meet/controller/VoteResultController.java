package com.workingdead.meet.controller;

import com.workingdead.meet.dto.VoteResultDtos;
import com.workingdead.meet.service.VoteResultService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/votes")
@RequiredArgsConstructor
public class VoteResultController {
    
    private final VoteResultService voteResultService;
    
    @Operation(
            summary = "투표 결과 조회",
            description = "투표 진행 상황을 집계하여 상위 3개 결과를 반환합니다. " +
                          "정렬 기준: 최다 인원 > 우선순위 가중치 > 빠른 날짜"
    )
    @GetMapping("/{voteId}/result")
    public ResponseEntity<VoteResultDtos.VoteResultRes> getVoteResult(
            @PathVariable Long voteId) {
        
        VoteResultDtos.VoteResultRes result = voteResultService.getVoteResult(voteId);
        return ResponseEntity.ok(result);
    }
}