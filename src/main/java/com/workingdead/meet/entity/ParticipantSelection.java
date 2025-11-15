// package com.workingdead.meet.entity;

// import jakarta.persistence.*;
// import lombok.*;

// import java.time.LocalDate;

// @Entity
// @Table(name = "participant_selection",
//        uniqueConstraints = @UniqueConstraint(columnNames = {"vote_id", "participant_id", "date", "period"}))
// @Getter
// @Setter
// @NoArgsConstructor
// @AllArgsConstructor
// @Builder
// public class ParticipantSelection {
//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     private Long id;

//     // 참조 Vote
//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "vote_id", nullable = false)
//     private Vote vote;

//     // 참조 Participant
//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "participant_id", nullable = false)
//     private Participant participant;

//     @Column(name = "date", nullable = false)
//     private LocalDate date;

//     @Enumerated(EnumType.STRING)
//     @Column(name = "period", nullable = false)
//     private Period period;

//     @Column(name = "selected", nullable = false)
//     private boolean selected;
// }

// ParticipantSelection.java
// package com.workingdead.meet.domain;

// import jakarta.persistence.*;
// import lombok.*;

// import java.time.LocalDate;

package com.workingdead.meet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "participant_selection",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"vote_id", "participant_id", "date", "period"})
       })
public class ParticipantSelection {
    
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
    
    @Column(nullable = false)
    private boolean selected;
}