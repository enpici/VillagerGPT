package io.github.enpici.villager.life.blueprint;

import java.util.Locale;

public enum BuildingType {
    HOUSE,
    FOOD_STORAGE,
    MATERIAL_STORAGE,
    WORKSTATION_HUB,
    DEFENSIVE,
    GENERIC;

    public static BuildingType from(String value) {
        if (value == null || value.isBlank()) {
            return GENERIC;
        }
        try {
            return BuildingType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GENERIC;
        }
    }
}
