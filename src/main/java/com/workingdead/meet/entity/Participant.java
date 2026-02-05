package com.workingdead.meet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter 
@Setter 
@NoArgsConstructor
@AllArgsConstructor 
@Builder
@Entity
@Table(
        name = "participant",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_participant_vote_bot_user",
                        columnNames = {"vote_id", "bot_user_key"}
                )
        }
)public class Participant {
    
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id", nullable = false)
    private Vote vote;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "bot_user_key", nullable = false)
    private String botUserKey;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    
    private Boolean submitted = false;
    
    // 일정 선택 정보
    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ParticipantSelection> selections = new ArrayList<>();
    
    // 우선순위 정보
    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PriorityPreference> priorities = new ArrayList<>();
    
    // 편의 생성자
    public Participant(Vote vote, String displayName, String botUserKey) {
        this.vote = vote;
        this.displayName = displayName;
        this.botUserKey = botUserKey;
    }
}