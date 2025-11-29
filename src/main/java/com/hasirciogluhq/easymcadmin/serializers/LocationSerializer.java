package com.hasirciogluhq.easymcadmin.serializers;

import com.google.gson.JsonObject;
import org.bukkit.Location;

/**
 * Utility class for serializing player locations to JSON
 */
public class LocationSerializer {
    
    /**
     * Serialize player location to JSON object
     * 
     * @param loc Location to serialize
     * @return JsonObject with world, x, y, z, yaw, pitch
     */
    public static JsonObject serialize(Location loc) {
        JsonObject locObj = new JsonObject();
        if (loc == null) {
            return locObj;
        }

        locObj.addProperty("world", loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
        locObj.addProperty("x", loc.getX());
        locObj.addProperty("y", loc.getY());
        locObj.addProperty("z", loc.getZ());
        locObj.addProperty("yaw", loc.getYaw());
        locObj.addProperty("pitch", loc.getPitch());

        return locObj;
    }
    
    /**
     * Create empty location object for offline players
     * 
     * @return JsonObject with N/A values
     */
    public static JsonObject createEmpty() {
        JsonObject locObj = new JsonObject();
        locObj.addProperty("world", "N/A");
        locObj.addProperty("x", 0.0);
        locObj.addProperty("y", 0.0);
        locObj.addProperty("z", 0.0);
        locObj.addProperty("yaw", 0.0);
        locObj.addProperty("pitch", 0.0);
        return locObj;
    }
}

