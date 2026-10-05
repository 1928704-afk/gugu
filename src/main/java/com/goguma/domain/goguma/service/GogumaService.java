package com.goguma.domain.goguma.service;

import com.goguma.domain.goguma.dto.ActionHistoryResponse;
import com.goguma.domain.goguma.dto.GogumaAddRequest;
import com.goguma.domain.goguma.dto.GogumaGrowRequest;
import com.goguma.domain.goguma.dto.GogumaResponse;
import com.goguma.domain.goguma.entity.ActionType;
import com.goguma.domain.goguma.entity.Goguma;
import com.goguma.domain.goguma.entity.GogumaAction;
import com.goguma.domain.goguma.repository.GogumaActionRepository;
import com.goguma.domain.goguma.repository.GogumaRepository;
import com.goguma.domain.user.entity.User;
import com.goguma.domain.user.repository.UserRepository;
import com.goguma.global.error.BusinessException;
import com.goguma.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GogumaService {

    private final GogumaRepository gogumaRepository;
    private final GogumaActionRepository gogumaActionRepository;
    private final UserRepository userRepository;

    @Transactional
    public GogumaResponse addGoguma(Long userId, GogumaAddRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Goguma goguma = Goguma.builder()
                .user(user)
                .name(request.getName().trim())
                .relation(request.getRelation() != null ? request.getRelation().trim() : null)
                .age(request.getAge())
                .build();

        Goguma saved = gogumaRepository.save(goguma);
        return new GogumaResponse(saved, getActionScores(saved.getId()), getTodayActions(userId, saved.getId()));
    }

    @Transactional
    public List<GogumaResponse> getMyGogumas(Long userId) {
        List<Goguma> list = gogumaRepository.findByUserIdOrderByIdAsc(userId);
        if (list.isEmpty()) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                Goguma defaultGoguma = gogumaRepository.save(
                        Goguma.builder().user(user).name("새싹고구마").relation("나").age(1).build()
                );
                return List.of(new GogumaResponse(defaultGoguma, getActionScores(defaultGoguma.getId()), getTodayActions(userId, defaultGoguma.getId())));
            }
        }
        return list.stream()
                .map(g -> new GogumaResponse(g, getActionScores(g.getId()), getTodayActions(userId, g.getId())))
                .toList();
    }

    @Transactional
    public GogumaResponse grow(Long userId, GogumaGrowRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Long targetGogumaId = request.getEffectiveId();
        if (targetGogumaId == null) {
            List<Goguma> myGogumas = gogumaRepository.findByUserIdOrderByIdAsc(userId);
            if (!myGogumas.isEmpty()) {
                targetGogumaId = myGogumas.get(0).getId();
            } else {
                throw new BusinessException(ErrorCode.GOGUMA_NOT_FOUND);
            }
        }

        Goguma goguma = gogumaRepository.findById(targetGogumaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOGUMA_NOT_FOUND));

        ActionType actionType = ActionType.fromKey(request.getActionType());
        LocalDate today = LocalDate.now();

        // 고구마 성장치(HP) 증가 (낙관적 락에 의해 동시성 보호, 최대 100)
        goguma.addHp(actionType.getExpValue());
        if (goguma.getHp() > 100) {
            // max 100
        }

        // 활동 이력 기록
        GogumaAction action = GogumaAction.builder()
                .user(user)
                .goguma(goguma)
                .actionType(actionType)
                .actionDate(today)
                .build();
        try {
            gogumaActionRepository.save(action);
        } catch (Exception ignored) {
            // 동일 날짜 중복 액션인 경우에도 경험치는 올려주고 정상 응답
        }

        return new GogumaResponse(goguma, getActionScores(goguma.getId()), getTodayActions(userId, goguma.getId()));
    }

    public List<ActionHistoryResponse> getHistory(Long userId, Long gogumaId) {
        return gogumaActionRepository.findByGogumaIdOrderByCreatedAtDesc(gogumaId)
                .stream()
                .map(ActionHistoryResponse::new)
                .toList();
    }

    @Transactional
    public void removeGoguma(Long userId, Long gogumaId) {
        gogumaRepository.findById(gogumaId).ifPresent(gogumaRepository::delete);
    }

    private Map<String, Integer> getActionScores(Long gogumaId) {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("bible", 0);
        scores.put("prayer", 0);
        scores.put("contact", 0);
        scores.put("invite", 0);
        scores.put("postWrite", 0);

        List<GogumaAction> actions = gogumaActionRepository.findByGogumaIdOrderByCreatedAtDesc(gogumaId);
        for (GogumaAction a : actions) {
            String key = a.getActionType().getKey();
            scores.put(key, scores.getOrDefault(key, 0) + 1);
        }
        return scores;
    }

    private Map<String, Boolean> getTodayActions(Long userId, Long gogumaId) {
        Map<String, Boolean> today = new HashMap<>();
        today.put("bible", false);
        today.put("prayer", false);
        today.put("contact", false);
        today.put("invite", false);
        today.put("postWrite", false);

        List<GogumaAction> actions = gogumaActionRepository.findByGogumaIdOrderByCreatedAtDesc(gogumaId);
        LocalDate now = LocalDate.now();
        for (GogumaAction a : actions) {
            if (a.getActionDate().isEqual(now)) {
                today.put(a.getActionType().getKey(), true);
            }
        }
        return today;
    }
}
