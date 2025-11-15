package com.workingdead.meet.controller;

import com.workingdead.meet.dto.VoteDateRangeDtos.DateSlotDto;
import com.workingdead.meet.service.VoteDateRangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Vote", description = "투표 관련 API")
@RestController
@RequestMapping("/votes")
public class VoteDateRangeController {
    private final VoteDateRangeService voteDateRangeService;

    public VoteDateRangeController(VoteDateRangeService voteDateRangeService) {
        this.voteDateRangeService = voteDateRangeService;
    }

    @Operation(summary = "투표 날짜 범위 조회 (날짜별 LUNCH/ DINNER 슬롯 제공)")
    @GetMapping("/{voteId}/dateRange")
    public ResponseEntity<List<DateSlotDto>> getDateRange(
            @PathVariable Long voteId,
            @RequestParam(required = false) Long participantId // optional
    ) {
        List<DateSlotDto> slots = voteDateRangeService.getDateRangeSlots(voteId, participantId);
        return ResponseEntity.ok(slots);
    }
}
