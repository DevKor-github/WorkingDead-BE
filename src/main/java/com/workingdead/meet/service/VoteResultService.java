package com.workingdead.meet.service;

import com.workingdead.meet.dto.VoteResultDtos.*;
import com.workingdead.meet.entity.*;
import com.workingdead.meet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteResultService {
    
    private final VoteRepository voteRepository;
    private final ParticipantSelectionRepository selectionRepository;
    private final PriorityPreferenceRepository priorityRepository;
    
    public VoteResultRes getVoteResult(Long voteId) {
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new NoSuchElementException("투표를 찾을 수 없습니다."));
        
        // 1. 모든 선택 가져오기 (selected = true만)
        List<ParticipantSelection> selections = selectionRepository.findByVoteId(voteId)
                .stream()
                .filter(ParticipantSelection::isSelected)
                .toList();
        
        // 2. 모든 우선순위 가져오기
        List<PriorityPreference> priorities = priorityRepository.findByVoteId(voteId);
        
        // 3. 날짜+시간대별로 그룹화
        Map<String, List<ParticipantSelection>> groupedSelections = selections.stream()
                .collect(Collectors.groupingBy(s -> keyOf(s.getDate(), s.getPeriod())));
        
        // 4. 우선순위 맵 생성
        Map<String, Map<Long, PriorityPreference>> priorityMap = new HashMap<>();
        for (PriorityPreference pref : priorities) {
            String key = keyOf(pref.getDate(), pref.getPeriod());
            priorityMap.computeIfAbsent(key, k -> new HashMap<>())
                    .put(pref.getParticipant().getId(), pref);
        }
        
        // 5. 각 날짜+시간대별 점수 계산
        List<SlotScore> slotScores = new ArrayList<>();

        for (Map.Entry<String, List<ParticipantSelection>> entry : groupedSelections.entrySet()) {
        String key = entry.getKey();
        List<ParticipantSelection> slotSelections = entry.getValue();
        
        if (slotSelections.isEmpty()) continue;
        
        LocalDate date = slotSelections.get(0).getDate();
        String period = slotSelections.get(0).getPeriod();
        
        int voteCount = slotSelections.size();
        double priorityScore = 0.0;
        int priorityIndexSum = 0;  // 추가!
        
        List<VoterDetailRes> voters = new ArrayList<>();
        
        for (ParticipantSelection selection : slotSelections) {
                Long participantId = selection.getParticipant().getId();
                String participantName = selection.getParticipant().getDisplayName();
                
                PriorityPreference pref = priorityMap.getOrDefault(key, Collections.emptyMap())
                        .get(participantId);
                
                Integer priorityIndex = pref != null ? pref.getPriorityIndex() : null;
                Double weight = pref != null ? pref.getWeight() : null;
                
                if (weight != null) {
                priorityScore += weight;
                }
                
                // priorityIndex 합계 (없으면 999로 처리해서 뒤로 밀림)
                if (priorityIndex != null) {
                priorityIndexSum += priorityIndex;
                } else {
                priorityIndexSum += 999;
                }
                
                voters.add(new VoterDetailRes(participantId, participantName, priorityIndex, weight));
        }
        
        slotScores.add(new SlotScore(date, period, voteCount, priorityScore, priorityIndexSum, voters));
        }

        // 6. 정렬: 최다 인원 > priorityIndex 합계 작을수록 상위
        slotScores.sort(Comparator
                .comparingInt(SlotScore::voteCount).reversed()           // 1. 인원수 많은 순
                .thenComparingInt(SlotScore::priorityIndexSum)           // 2. priorityIndex 합계 작은 순
                .thenComparing(SlotScore::date));                        // 3. 날짜 빠른 순

        // 7. 모든 결과에 순위 부여
        List<RankingRes> rankings = new ArrayList<>();

        for (int i = 0; i < slotScores.size(); i++) {
        SlotScore slot = slotScores.get(i);
        Integer rank = i < 3 ? (i + 1) : null;
        
        rankings.add(new RankingRes(
                rank,
                slot.date(),
                slot.period(),
                slot.voteCount(),
                slot.priorityScore(),
                slot.voters()
        ));
        }

        return new VoteResultRes(vote.getId(), vote.getName(), rankings);
        }
    
    private String keyOf(LocalDate date, String period) {
        return date.toString() + "|" + period;
    }
    
    // 내부 DTO (정렬용)
    private record SlotScore(
        LocalDate date,
        String period,
        int voteCount,
        double priorityScore,
        int priorityIndexSum,      // 추가!
        List<VoterDetailRes> voters
        ) {}
    
}