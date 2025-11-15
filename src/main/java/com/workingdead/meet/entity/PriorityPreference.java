package com.workingdead.meet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "priority_preference",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"participant_id", "vote_id", "date", "period", "priority_index"})
       })
public class PriorityPreference {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id", nullable = false)
    private Vote vote;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Column(nullable = false)
    private String period;
    
    @Column(name = "priority_index", nullable = false)
    private Integer priorityIndex;
    
    @Column(nullable = false)
    private Double weight;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}