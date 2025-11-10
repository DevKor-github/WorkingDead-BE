package com.workingdead.meet.dto;

import jakarta.validation.constraints.*;


public class ParticipantDtos {
    public record CreateParticipantReq(@NotBlank String displayName) {}
    public record ParticipantRes(Long id, String displayName) {}
}