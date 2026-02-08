package com.workingdead.meet.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "vote_notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_vote_notification_vote_type", columnNames = {"vote_id", "type"})
)
public class VoteNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vote_id", nullable = false)
    private Long voteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected VoteNotification() {}

    public VoteNotification(Long voteId, NotificationType type) {
        this.voteId = voteId;
        this.type = type;
        this.sentAt = Instant.now();
    }

}
