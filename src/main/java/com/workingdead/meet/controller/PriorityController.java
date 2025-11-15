// package com.workingdead.meet.controller;

// import com.workingdead.meet.dto.PriorityDtos.*;
// import com.workingdead.meet.service.PriorityService;
// import io.swagger.v3.oas.annotations.Operation;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.*;

// import jakarta.servlet.http.HttpSession;

// @RestController
// @RequestMapping("/participants")
// public class PriorityController {
//     private final PriorityService priorityService;

//     public PriorityController(PriorityService priorityService) {
//         this.priorityService = priorityService;
//     }

//     @Operation(summary = "참여자 우선순위 설정 (dryRun 가능, storage: db/session/redis)")
//     @PostMapping("/{participantId}")
//     public ResponseEntity<PriorityResponse> setPriorities(
//             @PathVariable Long participantId,
//             @RequestParam Long voteId,
//             @RequestParam(name = "storage", required = false, defaultValue = "db") String storage,
//             @RequestParam(name = "dryRun", required = false, defaultValue = "false") boolean dryRun,
//             @RequestBody PriorityRequest req,
//             HttpSession session
//     ) {
//         PriorityResponse res = priorityService.setPriorities(participantId, voteId, req, storage, dryRun, session);
//         if (dryRun) {
//             // 200 OK with diff
//             return ResponseEntity.ok(res);
//         } else {
//             // 200 OK with persisted result
//             return ResponseEntity.ok(res);
//         }
//     }
// }
