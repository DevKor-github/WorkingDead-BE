package com.workingdead.meet.repository;

import com.workingdead.meet.entity.ParticipantSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantSelectionRepository extends JpaRepository<ParticipantSelection, Long> {
    List<ParticipantSelection> findByVoteId(Long voteId);
    List<ParticipantSelection> findByVoteIdAndParticipantId(Long voteId, Long participantId);

    @Modifying
    @Query("DELETE FROM ParticipantSelection ps WHERE ps.participant.id = :participantId")
    void deleteByParticipantId(@Param("participantId") Long participantId);
}
