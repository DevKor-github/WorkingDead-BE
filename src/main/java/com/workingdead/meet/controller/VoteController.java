package com.workingdead.meet.controller;

import com.workingdead.meet.dto.*;
import com.workingdead.meet.entity.*;
import com.workingdead.meet.repository.*;
import com.workingdead.meet.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;


@RestController
@RequestMapping("/votes")
public class VoteController {
    private final VoteService voteService;
    public VoteController(VoteService voteService) { this.voteService = voteService; }


    // 0.1 홈화면 리스트 & 생성
    @GetMapping
    public List<VoteDtos.VoteSummary> list() { return voteService.listAll(); }


    @PostMapping
    public ResponseEntity<VoteDtos.VoteSummary> create(@RequestBody @Valid VoteDtos.CreateVoteReq req) {
        var res = voteService.create(req.name());
// 0.2.3 링크 복사: res.shareUrl 포함
        return ResponseEntity.ok(res);
    }


    // 0.2 투표 설정 화면 읽기/수정/삭제
    @GetMapping("/{id}")
    public VoteDtos.VoteDetail get(@PathVariable Long id) { return voteService.get(id); }


    @PatchMapping("/{id}")
    public VoteDtos.VoteDetail update(@PathVariable Long id, @RequestBody VoteDtos.UpdateVoteReq req) {
// 날짜 범위 검증은 서비스에서 처리 (end >= start)
        return voteService.update(id, req);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        voteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
