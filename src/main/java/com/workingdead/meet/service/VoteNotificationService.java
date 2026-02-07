package com.workingdead.meet.service;

import com.workingdead.meet.entity.NotificationType;
import com.workingdead.meet.entity.VoteNotification;
import com.workingdead.meet.repository.VoteNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteNotificationService {

    private final VoteNotificationRepository repo;

    /**
     * 최초 발송이면 true, 이미 발송한 타입이면 false
     * - (vote_id, type) unique 제약으로 동시성 안전
     */
    @Transactional
    public boolean markSentIfFirst(Long voteId, NotificationType type) {
        try {
            repo.save(new VoteNotification(voteId, type));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
