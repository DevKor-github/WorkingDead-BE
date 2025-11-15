package com.workingdead.meet.repository;

import com.workingdead.meet.entity.PriorityPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriorityPreferenceRepository extends JpaRepository<PriorityPreference, Long> {
    List<PriorityPreference> findByParticipantIdAndVoteId(Long participantId, Long voteId);
    List<PriorityPreference> findByVoteId(Long voteId); 
    
    void deleteByParticipantIdAndVoteId(Long participantId, Long voteId);
}
