package com.goguma.domain.goguma.dto;

import com.goguma.domain.goguma.entity.GogumaAction;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class ActionHistoryResponse {

    private final Long id;
    private final String actionKey;
    private final String actionLabel;
    private final int expValue;
    private final LocalDate actionDate;

    public ActionHistoryResponse(GogumaAction action) {
        this.id = action.getId();
        this.actionKey = action.getActionType().getKey();
        this.actionLabel = action.getActionType().getLabel();
        this.expValue = action.getActionType().getExpValue();
        this.actionDate = action.getActionDate();
    }
}
