package io.github.enpici.villager.life.task.impl;

import io.github.enpici.villager.life.agent.Agent;
import io.github.enpici.villager.life.village.VillageAI;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Villager;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

final class TaskMovement {

    private TaskMovement() {
    }

    static Villager villager(Agent agent) {
        try {
            return agent.resolveVillager();
        } catch (IllegalStateException | NullPointerException exception) {
            return null;
        }
    }

    static Location randomNearby(Location origin, double radius) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = random.nextDouble(-radius, radius);
        double z = random.nextDouble(-radius, radius);
        Location target = origin.clone().add(x, 0, z);
        World world = target.getWorld();
        int highestY = world.getHighestBlockYAt(target);
        target.setY(highestY + 1);
        return target;
    }

    static boolean moveTo(Villager villager, Location target, double speed) {
        if (villager == null || target == null || target.getWorld() == null || villager.getWorld() != target.getWorld()) {
            return false;
        }
        return villager.getPathfinder().moveTo(target, speed);
    }

    static boolean reached(Villager villager, Location target, double distanceSquared) {
        return villager != null
                && target != null
                && villager.getWorld() == target.getWorld()
                && villager.getLocation().distanceSquared(target) <= distanceSquared;
    }

    static Monster nearestMonster(Villager villager, double radius) {
        if (villager == null) {
            return null;
        }
        double radiusSquared = radius * radius;
        return villager.getNearbyEntities(radius, radius / 2.0, radius).stream()
                .filter(entity -> entity instanceof Monster)
                .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(villager.getLocation())))
                .filter(entity -> entity.getLocation().distanceSquared(villager.getLocation()) <= radiusSquared)
                .map(entity -> (Monster) entity)
                .orElse(null);
    }

    static Location awayFrom(Entity threat, Villager villager, double distance) {
        if (threat == null || villager == null || threat.getWorld() != villager.getWorld()) {
            return randomNearby(villager != null ? villager.getLocation() : null, distance);
        }
        Location current = villager.getLocation();
        Location source = threat.getLocation();
        double dx = current.getX() - source.getX();
        double dz = current.getZ() - source.getZ();
        double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        Location target = current.clone().add((dx / length) * distance, 0, (dz / length) * distance);
        int highestY = target.getWorld().getHighestBlockYAt(target);
        target.setY(highestY + 1);
        return target;
    }
}
