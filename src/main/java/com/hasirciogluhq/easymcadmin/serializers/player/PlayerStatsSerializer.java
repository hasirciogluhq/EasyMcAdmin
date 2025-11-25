package com.hasirciogluhq.easymcadmin.serializers.player;

import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Utility class for serializing player statistics to JSON payloads.
 */
public class PlayerStatsSerializer {

    public static JsonObject getPlayerStatsPayload(Player player) {
        // Root payload keeps player identity and grouped stats
        JsonObject root = new JsonObject();
        root.addProperty("uuid", player.getUniqueId().toString());
        root.addProperty("username", player.getName());

        JsonObject stats = new JsonObject();
        stats.add("general", buildGeneral(player));
        stats.add("distance", buildDistance(player));
        stats.add("blocks_broken", buildBlocksBroken(player));
        stats.add("items_used", buildItemsUsed(player));
        stats.add("entities_killed", buildEntitiesKilled(player));

        root.add("stats", stats);
        return root;
    }

    private static JsonObject buildGeneral(Player player) {
        JsonObject general = new JsonObject();
        long playTimeMinutes = Math.round(player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200.0);
        int playerKills = safeStatistic(player, Statistic.PLAYER_KILLS);
        int deaths = safeStatistic(player, Statistic.DEATHS);
        double kdr = deaths == 0 ? playerKills : (double) playerKills / deaths;

        general.addProperty("playtime_minutes", playTimeMinutes);
        general.addProperty("player_kills", playerKills);
        general.addProperty("deaths", deaths);
        general.addProperty("kdr", kdr);
        return general;
    }

    private static JsonObject buildDistance(Player player) {
        JsonObject distance = new JsonObject();

        long sprint = statToBlocks(player, Statistic.SPRINT_ONE_CM);
        long walk = statToBlocks(player, Statistic.WALK_ONE_CM);
        long fall = statToBlocks(player, Statistic.FALL_ONE_CM);
        long crouch = statToBlocks(player, Statistic.CROUCH_ONE_CM);
        long walkOnWater = statToBlocks(player, Statistic.WALK_ON_WATER_ONE_CM);
        long walkUnderWater = statToBlocks(player, Statistic.WALK_UNDER_WATER_ONE_CM);
        long swim = statToBlocks(player, Statistic.SWIM_ONE_CM);
        long climb = statToBlocks(player, Statistic.CLIMB_ONE_CM);

        long total = sprint + walk + fall + crouch + walkOnWater + walkUnderWater + swim + climb;

        distance.addProperty("total", total);
        distance.addProperty("sprint", sprint);
        distance.addProperty("walk", walk);
        distance.addProperty("fall", fall);
        distance.addProperty("crouch", crouch);
        distance.addProperty("walk_on_water", walkOnWater);
        distance.addProperty("walk_under_water", walkUnderWater);
        distance.addProperty("swim", swim);
        distance.addProperty("climb", climb);
        return distance;
    }

    private static JsonObject buildBlocksBroken(Player player) {
        JsonObject blocksBroken = new JsonObject();
        JsonObject details = new JsonObject();
        long total = 0;

        for (Material material : Material.values()) {
            if (!material.isBlock()) {
                continue;
            }

            int value = safeStatistic(player, Statistic.MINE_BLOCK, material);
            if (value > 0) {
                details.addProperty(material.getKey().getKey(), value);
                total += value;
            }
        }

        blocksBroken.addProperty("total", total);
        blocksBroken.add("details", details);
        return blocksBroken;
    }

    private static JsonObject buildItemsUsed(Player player) {
        JsonObject itemsUsed = new JsonObject();
        JsonObject details = new JsonObject();
        long total = 0;

        for (Material material : Material.values()) {
            if (!material.isItem()) {
                continue;
            }

            int value = safeStatistic(player, Statistic.USE_ITEM, material);
            if (value > 0) {
                details.addProperty(material.getKey().getKey(), value);
                total += value;
            }
        }

        itemsUsed.addProperty("total", total);
        itemsUsed.add("details", details);
        return itemsUsed;
    }

    private static JsonObject buildEntitiesKilled(Player player) {
        JsonObject entitiesKilled = new JsonObject();
        JsonObject details = new JsonObject();
        long total = 0;

        for (EntityType entityType : EntityType.values()) {
            if (!entityType.isAlive()) {
                continue;
            }

            int value = safeStatistic(player, Statistic.KILL_ENTITY, entityType);
            if (value > 0) {
                String key = entityType.getKey() != null ? entityType.getKey().getKey() : entityType.name().toLowerCase();
                details.addProperty(key, value);
                total += value;
            }
        }

        entitiesKilled.addProperty("total", total);
        entitiesKilled.add("details", details);
        return entitiesKilled;
    }

    private static int safeStatistic(Player player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int safeStatistic(Player player, Statistic statistic, Material material) {
        try {
            return player.getStatistic(statistic, material);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int safeStatistic(Player player, Statistic statistic, EntityType entityType) {
        try {
            return player.getStatistic(statistic, entityType);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long statToBlocks(Player player, Statistic statistic) {
        return Math.round(safeStatistic(player, statistic) / 100.0);
    }
}
