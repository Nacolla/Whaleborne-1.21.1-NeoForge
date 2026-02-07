package com.fruityspikes.whaleborne.server.entities.components.hullback;

import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import com.fruityspikes.whaleborne.server.entities.HullbackPartEntity;
import com.fruityspikes.whaleborne.server.entities.HullbackWalkableEntity;
import com.fruityspikes.whaleborne.server.registries.WBEntityRegistry;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;

public class HullbackPartManager {
    private final HullbackEntity hullback;
    public final HullbackPartEntity[] subEntities;
    
    // Position/Rotation Arrays
    public final Vec3[] prevPartPositions = new Vec3[5];
    public final Vec3[] partPosition = new Vec3[5];
    public final float[] partYRot = new float[5];
    public final float[] partXRot = new float[5];
    public final Vec3[] oldPartPosition = new Vec3[5];
    public final float[] oldPartYRot = new float[5];
    public final float[] oldPartXRot = new float[5];
    
    // Seats
    public final Vec3[] seats = new Vec3[7];
    public final Vec3[] oldSeats = new Vec3[7];

    private static final Vec3[] SEAT_OFFSETS = {
            new Vec3(0, 5.5f, 0.0), //sail
            new Vec3(0, 5.5f, -3.0), //captain
            new Vec3(1.5, 5.5f, 0.3),
            new Vec3(-1.5, 5.5f, 0.3),
            new Vec3(1.5, 5.5f, -1.75),
            new Vec3(-1.5, 5.5f, -1.75),
            new Vec3(0, 1.6f, -0.8) //fluke
    };

    public HullbackPartManager(HullbackEntity hullback, HullbackPartEntity[] subEntities) {
        this.hullback = hullback;
        this.subEntities = subEntities;
        Arrays.fill(partPosition, Vec3.ZERO);
    }

    public void init() {
         // Initial allocation logic if needed, currently empty as arrays are final
    }

    public void setOldPosAndRots() {
        for (int i = 0; i < 5; i++) {
            this.oldPartPosition[i] = subEntities[i].position();
            this.oldPartYRot[i] = subEntities[i].getYRot();
            this.oldPartXRot[i] = subEntities[i].getXRot();
        }
    }

    public void updatePartPositions() {
        float[] partDragFactors = new float[]{1f, 0.9f, 0.2f, 0.1f, 0.09f};

        Vec3[] baseOffsets = {
                new Vec3(0, 0, 6),      // Nose
                new Vec3(0, 0, 2.5),    // Head
                new Vec3(0, 0, -2.25),  // Body
                new Vec3(0, 0, -7),     // Tail
                new Vec3(0, 0, -11)     // Fluke
        };

        if (prevPartPositions[0] == null) {
            float yawRadInit = -hullback.getYRot() * Mth.DEG_TO_RAD;
            float pitchRadInit = hullback.getXRot() * Mth.DEG_TO_RAD;
            for (int i = 0; i < prevPartPositions.length; i++) {
                Vec3 initBaseOffset = switch(i) {
                     case 0 -> new Vec3(0, 0, 6);
                     case 1 -> new Vec3(0, 0, 2.5);
                     case 2 -> new Vec3(0, 0, -2.25);
                     case 3 -> new Vec3(0, 0, -7);
                     case 4 -> new Vec3(0, 0, -11);
                     default -> Vec3.ZERO;
                };
                
                Vec3 rotatedOffset = initBaseOffset.yRot(yawRadInit).xRot(pitchRadInit);
                prevPartPositions[i] = hullback.position().add(rotatedOffset);
            }
        }

        float swimCycle = (float) (Mth.sin(hullback.tickCount * 0.1f) * hullback.getDeltaMovement().length());
        float yawRad = -hullback.getYRot() * Mth.DEG_TO_RAD;
        float pitchRad = hullback.getXRot() * Mth.DEG_TO_RAD;

        for (int i = 0; i < baseOffsets.length; i++) {
            baseOffsets[i] = baseOffsets[i]
                    .yRot(yawRad)
                    .xRot(pitchRad);

            baseOffsets[i] = new Vec3(
                    hullback.getX() + baseOffsets[i].x,
                    hullback.getY() + baseOffsets[i].y,
                    hullback.getZ() + baseOffsets[i].z
            );

            if (i > 0) {
                baseOffsets[i] = new Vec3(
                        Mth.lerp(partDragFactors[i], prevPartPositions[i].x, baseOffsets[i].x),
                        Mth.lerp(partDragFactors[i], prevPartPositions[i].y, baseOffsets[i].y),
                        Mth.lerp(partDragFactors[i], prevPartPositions[i].z, baseOffsets[i].z)
                );
                
                double maxDist = switch(i) {
                     case 1 -> 3.55;
                     case 2 -> 4.8;
                     case 3 -> 4.8;
                     case 4 -> 4.1;
                     default -> 10.0;
                };
                
                Vec3 parentPos = prevPartPositions[i-1];
                double dist = baseOffsets[i].distanceTo(parentPos);
                if (dist > maxDist) {
                     baseOffsets[i] = parentPos.add(baseOffsets[i].subtract(parentPos).normalize().scale(maxDist));
                }
            }
            prevPartPositions[i] = baseOffsets[i];
        }

        this.partPosition[0] = prevPartPositions[0];
        this.partYRot[0] = calculateYaw(prevPartPositions[0], prevPartPositions[1]);
        this.partXRot[0] = calculatePitch(prevPartPositions[0], prevPartPositions[1]);
        subEntities[0].moveTo(prevPartPositions[0].x, prevPartPositions[0].y, prevPartPositions[0].z,
                partYRot[0],
                partXRot[0]);

        this.partPosition[1] = new Vec3(prevPartPositions[1].x, prevPartPositions[1].y + swimCycle * 2, prevPartPositions[1].z);
        this.partYRot[1] = calculateYaw(prevPartPositions[0], prevPartPositions[1]);
        this.partXRot[1] = calculatePitch(prevPartPositions[0], prevPartPositions[1]);
        subEntities[1].moveTo(prevPartPositions[1].x, prevPartPositions[1].y + swimCycle * 2, prevPartPositions[1].z,
                partYRot[1],
                partXRot[1]);

        this.partPosition[2] = new Vec3(prevPartPositions[2].x, prevPartPositions[2].y + swimCycle * 2, prevPartPositions[2].z);
        this.partYRot[2] = calculateYaw(prevPartPositions[1], prevPartPositions[2]);
        this.partXRot[2] = calculatePitch(prevPartPositions[1], prevPartPositions[2]);
        subEntities[2].moveTo(prevPartPositions[2].x,
                prevPartPositions[2].y + swimCycle * 2,
                prevPartPositions[2].z,
                partYRot[2],
                partXRot[2]);

        this.partPosition[3] = new Vec3(prevPartPositions[3].x, prevPartPositions[3].y + swimCycle * 8, prevPartPositions[3].z);
        this.partYRot[3] = calculateYaw(prevPartPositions[2], prevPartPositions[3]);
        this.partXRot[3] = calculatePitch(prevPartPositions[2], prevPartPositions[3]) * 1.5f - swimCycle * 20f;
        subEntities[3].moveTo(prevPartPositions[3].x,
                prevPartPositions[3].y + swimCycle * 8,
                prevPartPositions[3].z,
                partYRot[3],
                partXRot[3]);

        float flukeDistance = 4.0f;
        Vec3 flukeOffset = new Vec3(0, 0, -flukeDistance)
                .yRot(-subEntities[3].getYRot() * Mth.DEG_TO_RAD)
                .xRot(subEntities[3].getXRot() * Mth.DEG_TO_RAD);

        Vec3 flukeTarget = new Vec3(
                subEntities[3].getX() + flukeOffset.x,
                subEntities[3].getY() + flukeOffset.y + swimCycle * 5.5,
                subEntities[3].getZ() + flukeOffset.z
        );

        float flukeYaw = calculateYaw(subEntities[3].position(), flukeTarget);
        float flukePitch = calculatePitch(subEntities[3].position(), flukeTarget);

        flukeYaw = Mth.rotLerp(partDragFactors[4], oldPartYRot[4], flukeYaw);
        float flukeXRot = flukePitch * 1.5f + swimCycle * 30f;

        this.partPosition[4] = flukeTarget;
        this.partYRot[4] = flukeYaw;
        this.partXRot[4] = flukeXRot;
        subEntities[4].moveTo(
                flukeTarget.x,
                flukeTarget.y,
                flukeTarget.z,
                flukeYaw,
                flukeXRot
        );

        // Calculate seat positions after updating part positions
        calculateSeats();
    }
    
    public void calculateSeats() {
        if (partPosition == null || partYRot == null || partXRot == null || partPosition[0] == null) return;
        
        seats[0] = partPosition[0].add((SEAT_OFFSETS[0]).xRot(partXRot[1] * Mth.DEG_TO_RAD).yRot(-partYRot[1] * Mth.DEG_TO_RAD));
        seats[1] = partPosition[0].add((SEAT_OFFSETS[1]).xRot(partXRot[1] * Mth.DEG_TO_RAD).yRot(-partYRot[1] * Mth.DEG_TO_RAD));
        seats[2] = partPosition[2].add((SEAT_OFFSETS[2]).xRot(partXRot[2] * Mth.DEG_TO_RAD).yRot(-partYRot[2] * Mth.DEG_TO_RAD));
        seats[3] = partPosition[2].add((SEAT_OFFSETS[3]).xRot(partXRot[2] * Mth.DEG_TO_RAD).yRot(-partYRot[2] * Mth.DEG_TO_RAD));
        seats[4] = partPosition[2].add((SEAT_OFFSETS[4]).xRot(partXRot[2] * Mth.DEG_TO_RAD).yRot(-partYRot[2] * Mth.DEG_TO_RAD));
        seats[5] = partPosition[2].add((SEAT_OFFSETS[5]).xRot(partXRot[2] * Mth.DEG_TO_RAD).yRot(-partYRot[2] * Mth.DEG_TO_RAD));
        seats[6] = partPosition[4].add((SEAT_OFFSETS[6]).xRot(partXRot[4] * Mth.DEG_TO_RAD).yRot(-partYRot[4] * Mth.DEG_TO_RAD));

        oldSeats[0] = oldPartPosition[0].add((SEAT_OFFSETS[0]).xRot(oldPartXRot[1] * Mth.DEG_TO_RAD).yRot(-oldPartYRot[1] * Mth.DEG_TO_RAD));
        oldSeats[1] = oldPartPosition[0].add((SEAT_OFFSETS[1]).xRot(oldPartXRot[1] * Mth.DEG_TO_RAD).yRot(-oldPartYRot[1] * Mth.DEG_TO_RAD));
        oldSeats[2] = oldPartPosition[2].add((SEAT_OFFSETS[2]).xRot(oldPartXRot[2] * Mth.DEG_TO_RAD).yRot(-oldPartYRot[2] * Mth.DEG_TO_RAD));
        oldSeats[3] = oldPartPosition[2].add((SEAT_OFFSETS[3]).xRot(oldPartXRot[2] * Mth.DEG_TO_RAD).yRot(-oldPartYRot[2] * Mth.DEG_TO_RAD));
        oldSeats[4] = oldPartPosition[2].add((SEAT_OFFSETS[4]).xRot(oldPartXRot[2] * Mth.DEG_TO_RAD).yRot(-oldPartYRot[2] * Mth.DEG_TO_RAD));
        oldSeats[5] = oldPartPosition[2].add((SEAT_OFFSETS[5]).xRot(partXRot[2] * Mth.DEG_TO_RAD).yRot(-partYRot[2] * Mth.DEG_TO_RAD));
        oldSeats[6] = oldPartPosition[4].add((SEAT_OFFSETS[6]).xRot(oldPartXRot[4] * Mth.DEG_TO_RAD).yRot(-oldPartYRot[4] * Mth.DEG_TO_RAD));
    }
    
    private float calculateYaw(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float)(Mth.atan2(dz, dx) * (180F / Math.PI)) + 90F;
    }

    private float calculatePitch(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        return -(float)(Mth.atan2(dy, horizontalDistance) * (180F / Math.PI));
    }
    
    public HullbackWalkableEntity spawnPlatform(int index) {
        if (!hullback.isDeadOrDying()) {
            HullbackWalkableEntity part = new HullbackWalkableEntity(WBEntityRegistry.HULLBACK_PLATFORM.get(), hullback.level());
            part.setPos(subEntities[index].getX(), hullback.position().y + 4.7, subEntities[index].getZ());
            if (hullback.level().addFreshEntity(part)) {
                return part;
            }
        }
        return null;
    }
}
