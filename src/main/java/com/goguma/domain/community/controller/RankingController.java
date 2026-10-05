package com.goguma.domain.community.controller;

import com.goguma.domain.community.dto.RankingResponse;
import com.goguma.domain.goguma.entity.Goguma;
import com.goguma.domain.goguma.repository.GogumaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final GogumaRepository gogumaRepository;

    @GetMapping
    public ResponseEntity<List<RankingResponse>> getRanking() {
        List<Goguma> gogumas = gogumaRepository.findAll();
        Map<String, List<Integer>> deptHps = new HashMap<>();
        Map<String, Set<Long>> deptUsers = new HashMap<>();

        deptHps.put("언약부", new ArrayList<>());
        deptHps.put("밀알부", new ArrayList<>());
        deptHps.put("이레부", new ArrayList<>());

        deptUsers.put("언약부", new HashSet<>());
        deptUsers.put("밀알부", new HashSet<>());
        deptUsers.put("이레부", new HashSet<>());

        for (Goguma g : gogumas) {
            String dept = g.getUser().getDepartment().getDescription();
            if (deptHps.containsKey(dept)) {
                deptHps.get(dept).add(g.getHp());
                deptUsers.get(dept).add(g.getUser().getId());
            }
        }

        List<RankingResponse> rankings = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : deptHps.entrySet()) {
            List<Integer> hps = entry.getValue();
            double avg = hps.isEmpty() ? 0.0 : hps.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            long userCount = deptUsers.get(entry.getKey()).size();
            rankings.add(new RankingResponse(
                    entry.getKey(),
                    Math.round(avg * 10.0) / 10.0,
                    hps.size(),
                    userCount
            ));
        }

        rankings.sort((a, b) -> Double.compare(b.getAvgHp(), a.getAvgHp()));
        return ResponseEntity.ok(rankings);
    }
}
