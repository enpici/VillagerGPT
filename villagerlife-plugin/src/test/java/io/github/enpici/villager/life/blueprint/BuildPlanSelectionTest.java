package io.github.enpici.villager.life.blueprint;

import io.github.enpici.villager.life.build.BlockPlacementStep;
import io.github.enpici.villager.life.build.BuildExecutor;
import io.github.enpici.villager.life.build.BuildPlan;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildPlanSelectionTest {

    @Test
    void enforcesPrerequisiteOrderWhenSelectingCurrentStep() {
        BlockPlacementStep foundation = new BlockPlacementStep(Material.COBBLESTONE, new Location(null, 0, 64, 0), List.of());
        BlockPlacementStep roof = new BlockPlacementStep(Material.OAK_PLANKS, new Location(null, 0, 65, 0), List.of(0));
        BuildExecutor executor = new BuildExecutor(new BuildPlan(List.of(foundation, roof)));

        assertSame(foundation, executor.currentStep());
        assertTrue(executor.arePrerequisitesSatisfied(foundation));
        assertFalse(executor.arePrerequisitesSatisfied(roof));

        executor.markCurrentPlaced();
        assertSame(roof, executor.currentStep());
        assertTrue(executor.arePrerequisitesSatisfied(roof));
    }
}
