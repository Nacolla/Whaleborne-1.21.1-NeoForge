package com.fruityspikes.whaleborne.server.entities.components.hullback;

import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

public class HullbackBodyRotationControl extends BodyRotationControl {
    private final HullbackEntity hullBack;

    public HullbackBodyRotationControl(HullbackEntity hullBack) {
        super(hullBack);
        this.hullBack = hullBack;
    }

    @Override
    public void clientTick() {
        hullBack.setYBodyRot(hullBack.getYRot());
    }
}
