package com.goguma.domain.goguma.repository;

import com.goguma.domain.goguma.entity.ActionType;
import com.goguma.domain.goguma.entity.GogumaAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GogumaActionRepository extends JpaRepository<GogumaAction, Long> {
    boolean existsByUserIdAndGogumaIdAndActionTypeAndActionDate(
            Long userId, Long gogumaId, ActionType actionType, LocalDate actionDate
    );

    List<GogumaAction> findByGogumaIdOrderByCreatedAtDesc(Long gogumaId);
}
