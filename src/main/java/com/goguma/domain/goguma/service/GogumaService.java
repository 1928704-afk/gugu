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
import java.util.List;

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

        return new GogumaResponse(gogumaRepository.save(goguma));
    }

    public List<GogumaResponse> getMyGogumas(Long userId) {
        return gogumaRepository.findByUserIdOrderByIdAsc(userId)
                .stream()
                .map(GogumaResponse::new)
                .toList();
    }

    @Transactional
    public GogumaResponse grow(Long userId, GogumaGrowRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Goguma goguma = gogumaRepository.findById(request.getGogumaId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GOGUMA_NOT_FOUND));

        if (!goguma.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        ActionType actionType = ActionType.fromKey(request.getActionType());
        LocalDate today = LocalDate.now();

        // 1. 일일 중복 수행 방지 검증
        boolean alreadyActed = gogumaActionRepository.existsByUserIdAndGogumaIdAndActionTypeAndActionDate(
                userId, goguma.getId(), actionType, today
        );
        if (alreadyActed) {
            throw new BusinessException(ErrorCode.ALREADY_ACTED_TODAY, "오늘 이미 완료한 활동입니다.");
        }

        // 2. 고구마 성장치(HP) 증가 (낙관적 락에 의해 동시성 보호)
        goguma.addHp(actionType.getExpValue());

        // 3. 활동 이력 기록
        GogumaAction action = GogumaAction.builder()
                .user(user)
                .goguma(goguma)
                .actionType(actionType)
                .actionDate(today)
                .build();
        gogumaActionRepository.save(action);

        return new GogumaResponse(goguma);
    }

    public List<ActionHistoryResponse> getHistory(Long userId, Long gogumaId) {
        Goguma goguma = gogumaRepository.findById(gogumaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOGUMA_NOT_FOUND));

        if (!goguma.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        return gogumaActionRepository.findByGogumaIdOrderByCreatedAtDesc(gogumaId)
                .stream()
                .map(ActionHistoryResponse::new)
                .toList();
    }

    @Transactional
    public void removeGoguma(Long userId, Long gogumaId) {
        Goguma goguma = gogumaRepository.findById(gogumaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOGUMA_NOT_FOUND));

        if (!goguma.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        gogumaRepository.delete(goguma);
    }
}
