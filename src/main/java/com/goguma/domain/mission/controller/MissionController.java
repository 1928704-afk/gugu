package com.goguma.domain.mission.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/missions")
public class MissionController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMissions() {
        Map<String, Object> daily = new HashMap<>();
        daily.put("key", "daily_core3");
        daily.put("completed", false);
        daily.put("claimed", false);
        daily.put("completedCount", 0);
        daily.put("target", 3);
        daily.put("rewardHp", 2);

        Map<String, Object> weekly = new HashMap<>();
        weekly.put("key", "weekly_w1_post3");
        weekly.put("label", "주간 미션 (1주차)");
        weekly.put("description", "게시판 작성 3회 달성");
        weekly.put("completed", false);
        weekly.put("claimed", false);
        weekly.put("progress", 0);
        weekly.put("target", 3);
        weekly.put("rewardHp", 6);

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
