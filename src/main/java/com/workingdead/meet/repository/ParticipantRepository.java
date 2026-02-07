package com.workingdead.meet.repository;

import com.workingdead.meet.entity.Participant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.Query;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findByVoteId(Long voteId);

    @Query("select p.botUserKey from Participant p where p.vote.id = :voteId and p.botUserKey in :keys")
    List<String> findBotUserKeysByVoteIdAndBotUserKeyIn(Long voteId, List<String> keys);

    Optional<Participant> findByVoteIdAndBotUserKey(Long voteId, String botUserKey);
}
