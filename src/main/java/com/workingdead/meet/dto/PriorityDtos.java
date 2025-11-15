package com.workingdead.meet.dto;

import java.time.LocalDate;
import java.util.List;

public class PriorityDtos {
    // 요청: 하나의 우선순위 항목
    public record PriorityItemReq(
            LocalDate date,
            String period,      // "LUNCH" or "DINNER"
            int priorityIndex   // 1..3
    ) {}

    // 전체 요청
    public record PriorityRequest(
            List<PriorityItemReq> items
    ) {}

    // 응답: 저장된 항목 표현
    public record PriorityItemRes(
            LocalDate date,
            String period,
            int priorityIndex,
            double weight
    ) {}

    // diff 응답: added/removed/updated (updated rarely used)
    public record PriorityDiff(
            List<PriorityItemRes> added,
            List<PriorityItemRes> removed,
            List<PriorityItemRes> unchanged
    ) {}

    // 전체 결과
    public record PriorityResponse(
            String storage, // "db", "session", "redis"
            boolean dryRun,
            PriorityDiff diff,
            List<PriorityItemRes> current // current state after operation (if dryRun=false DB updated; if session/redis stored etc)
    ) {}
}
