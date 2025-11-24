package com.workingdead.meet.repository;

import com.workingdead.meet.entity.PriorityPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriorityPreferenceRepository extends JpaRepository<PriorityPreference, Long> {
    List<PriorityPreference> findByParticipantIdAndVoteId(Long participantId, Long voteId);
    List<PriorityPreference> findByVoteId(Long voteId); 
    
    // void deleteByParticipantIdAndVoteId(Long participantId, Long voteId);
    @Modifying
    @Query("DELETE FROM PriorityPreference pp WHERE pp.participant.id = :participantId")
    void deleteByParticipantId(@Param("participantId") Long participantId);
}
