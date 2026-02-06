package com.workingdead.meet.repository;

import com.workingdead.meet.entity.Vote;
import com.workingdead.meet.entity.Vote.VoteStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findByCode(String code);
    Optional<Vote> findTopByBotGroupKeyAndStatusOrderByCreatedAtDesc(String botGroupKey, Vote.VoteStatus status);

    List<Vote> findAllByStatus(VoteStatus status);
}
