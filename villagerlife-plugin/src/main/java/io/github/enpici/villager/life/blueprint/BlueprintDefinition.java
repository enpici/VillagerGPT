package io.github.enpici.villager.life.blueprint;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record BlueprintDefinition(
        String id,
        File schemFile,
        BuildingType type,
        int capacity,
        Set<String> tags
) {

    public BlueprintDefinition {
        tags = tags == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(tags));
    }

    public boolean hasTag(String tag) {
        return tag != null && tags.contains(tag.toLowerCase());
    }
}
