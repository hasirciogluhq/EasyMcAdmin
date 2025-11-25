package com.hasirciogluhq.easymcadmin.interfaces.player.stats;

import com.google.gson.JsonObject;

public interface PlayerStatsDispatcherInterface {
    void dispatch(String statsHash, String previousHash, Boolean fullSync, JsonObject event);
}
