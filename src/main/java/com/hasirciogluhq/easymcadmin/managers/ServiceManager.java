package com.hasirciogluhq.easymcadmin.managers;

import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.services.player.PlayerService;

public class ServiceManager {
    private PlayerService playerService;

    public ServiceManager(EasyMcAdmin ema){
        this.playerService = new PlayerService(ema);
    }
}
