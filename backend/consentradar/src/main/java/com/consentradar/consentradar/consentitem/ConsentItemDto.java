package com.consentradar.consentradar.consentitem;

import com.consentradar.consentradar.entity.ConsentItem;

public record ConsentItemDto(
        ConsentItem.ItemType itemType,
        String itemName,
        int dsScore,
        int esScore,
        int tfScore,
        double pcScore,
        double aiScore
) {
}