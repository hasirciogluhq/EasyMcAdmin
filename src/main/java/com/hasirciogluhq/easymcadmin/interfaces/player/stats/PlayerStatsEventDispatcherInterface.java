package com.hasirciogluhq.easymcadmin.interfaces.player.stats;

import com.google.gson.JsonObject;

public interface PlayerStatsEventDispatcherInterface {
    void dispatch(JsonObject event);
}
