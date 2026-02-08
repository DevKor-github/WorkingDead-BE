package com.workingdead.meet.repository;

import com.workingdead.meet.entity.NotificationType;
import com.workingdead.meet.entity.VoteNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteNotificationRepository extends JpaRepository<VoteNotification, Long> {
    boolean existsByVoteIdAndType(Long voteId, NotificationType type);
}
