package com.workingdead.meet.repository;

import com.workingdead.meet.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface VoteRepository extends JpaRepository<Vote, Long> {
    Optional<Vote> findByCode(String code);
}
