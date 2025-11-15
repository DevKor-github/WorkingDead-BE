package com.workingdead.meet.dto;

import java.time.LocalDate;
import java.util.List;

public class VoteDateRangeDtos {
    public record SlotDto(String period, boolean selected) {}
    public record DateSlotDto(LocalDate date, List<SlotDto> slots) {}
}
