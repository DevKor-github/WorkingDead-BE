package com.workingdead.meet.repository;

import com.workingdead.meet.entity.ParticipantSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ParticipantSelectionRepository extends JpaRepository<ParticipantSelection, Long> {
    List<ParticipantSelection> findByVoteId(Long voteId);
    List<ParticipantSelection> findByVoteIdAndParticipantId(Long voteId, Long participantId);
}
