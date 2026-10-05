package com.goguma.domain.mission.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMissions() {
        LocalDate today = LocalDate.now();

        Map<String, Object> daily = new HashMap<>();
        daily.put("key", "daily_core3");
        daily.put("label", "오늘의 3종 미션");
        daily.put("periodKey", today.toString());
        daily.put("rewardHp", 2);
        daily.put("requiredActions", List.of("bible", "prayer", "contact"));
        daily.put("completedActions", Collections.emptyList());
        daily.put("progress", Map.of("current", 0, "total", 3));
        daily.put("completed", false);
        daily.put("claimed", false);

        Map<String, Object> weekly = new HashMap<>();
        weekly.put("key", "weekly_w1_post3");
        weekly.put("label", "주간 미션 (1주차)");
        weekly.put("description", "게시판 작성 3회 달성");
        weekly.put("periodKey", today.toString());
        weekly.put("rewardHp", 6);
        weekly.put("weekStart", today.toString());
        weekly.put("weekEnd", today.plusDays(6).toString());
        weekly.put("progress", Map.of("current", 0, "total", 3));
        weekly.put("completed", false);
        weekly.put("claimed", false);

        Map<String, Object> result = new HashMap<>();
        result.put("daily", daily);
        result.put("weekly", weekly);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{key}/claim")
    public ResponseEntity<Map<String, Object>> claimReward(@PathVariable("key") String key) {
        return ResponseEntity.ok(Map.of("ok", true, "rewardHp", 2));
    }
}
