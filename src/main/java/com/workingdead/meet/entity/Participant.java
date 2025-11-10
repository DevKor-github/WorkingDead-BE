package com.workingdead.meet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor @Builder
@Entity
@Table(name = "participant")
public class Participant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id", nullable = false)
    private Vote vote; // FK to vote


    @Column(nullable = false)
    private String displayName; // 칩에 보여줄 이름


//    @Column(nullable = false, unique = true)
//    private String loginCode; // 간단 로그인 토큰(칩 생성용)


    public Participant() {}
    public Participant(Vote vote, String displayName) {
        this.vote = vote;
        this.displayName = displayName;
//        this.loginCode = loginCode;
    }


    // getters/setters
}
