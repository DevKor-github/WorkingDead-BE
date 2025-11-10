package com.workingdead.meet.controller;

import com.workingdead.meet.dto.*;
import com.workingdead.meet.entity.*;
import com.workingdead.meet.repository.*;
import com.workingdead.meet.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("")
public class ParticipantController {
    private final ParticipantService participantService;
    public ParticipantController(ParticipantService participantService) { this.participantService = participantService; }


    // 0.2 참여자 추가/삭제
    @PostMapping("/votes/{voteId}/participants")
    public ResponseEntity<ParticipantDtos.ParticipantRes> add(@PathVariable Long voteId, @RequestBody @Valid ParticipantDtos.CreateParticipantReq req) {
        var res = participantService.add(voteId, req.displayName());
        return ResponseEntity.ok(res);
    }


    @DeleteMapping("/participants/{participantId}")
    public ResponseEntity<Void> remove(@PathVariable Long participantId) {
        participantService.remove(participantId);
        return ResponseEntity.noContent().build();
    }
}
