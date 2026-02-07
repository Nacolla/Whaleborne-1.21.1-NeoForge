package com.fruityspikes.whaleborne.server.entities.components.hullback;

import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class HullbackSeatManager {
    private final HullbackEntity whale;
    private final EntityDataAccessor<Optional<UUID>>[] seatAccessors;
    public final Vec3[] seats;
    public final Vec3[] oldSeats;

    @SuppressWarnings("unchecked")
    public HullbackSeatManager(HullbackEntity whale, EntityDataAccessor<Optional<UUID>>... accessors) {
        this.whale = whale;
        this.seatAccessors = accessors;
        this.seats = new Vec3[accessors.length];
        this.oldSeats = new Vec3[accessors.length];
    }

    public void tick() {
        // Validate seat assignments every tick
        validateAssignments();
    }

    public void updateSeats(Vec3[] newSeats, Vec3[] newOldSeats) {
        // Copy seat positions from partManager
        System.arraycopy(newSeats, 0, this.seats, 0, Math.min(newSeats.length, this.seats.length));
        System.arraycopy(newOldSeats, 0, this.oldSeats, 0, Math.min(newOldSeats.length, this.oldSeats.length));
    }

    public int getSeatCount() {
        return seatAccessors.length;
    }
    
    public Optional<UUID> getSeatData(int index) {
        if (index < 0 || index >= seatAccessors.length) return Optional.empty();
        return whale.getEntityData().get(seatAccessors[index]);
    }

    public void setSeatData(int index, Optional<UUID> uuid) {
         if (index >= 0 && index < seatAccessors.length) {
             whale.getEntityData().set(seatAccessors[index], uuid);
         }
    }

    public EntityDataAccessor<Optional<UUID>> getSeatAccessor(int seatIndex) {
        if (seatIndex < 0 || seatIndex >= seatAccessors.length) {
            return seatAccessors[seatAccessors.length - 1]; // Default to last seat
        }
        return seatAccessors[seatIndex];
    }

    public void validateAssignments() {
        if (whale.level().isClientSide) return;

        java.util.Set<UUID> currentPassengerUUIDs = whale.getPassengers().stream()
                .map(Entity::getUUID)
                .collect(java.util.stream.Collectors.toSet());

        for (int seatIndex = 0; seatIndex < seatAccessors.length; seatIndex++) {
            EntityDataAccessor<Optional<UUID>> seatAccessor = getSeatAccessor(seatIndex);
            Optional<UUID> assignedUUID = whale.getEntityData().get(seatAccessor);

            if (assignedUUID.isPresent()) {
                UUID uuid = assignedUUID.get();

                if (!currentPassengerUUIDs.contains(uuid)) {
                    boolean shouldClear = true;

                    // "Smart" Check & Active Recovery
                    if (whale.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        Entity passengerEntity = serverLevel.getEntity(uuid);
                        
                        if (passengerEntity == null) {
                            // Entity still loading or dead. Wait up to 60s.
                            if (whale.tickCount < 1200) { 
                                shouldClear = false;
                            }
                        } else {
                            // Entity found but deemed "detached" (not in our passenger list).
                            // RECOVERY LOGIC:
                            // 1. Teleport to seat (catch up if whale moved)
                            // 2. Force mount
                            
                            if (passengerEntity.isAlive()) {
                                Vec3 seatPos = seats[seatIndex]; // Current seat position
                                // Teleport to sync position before mounting to avoid weird interpolation
                                passengerEntity.teleportTo(seatPos.x, seatPos.y, seatPos.z);
                                
                                // Force re-mount
                                passengerEntity.startRiding(whale, true);
                                
                                // Do NOT clear the seat, we just fixed it.
                                shouldClear = false;
                            }
                        }
                    }

                    if (shouldClear) {
                        whale.getEntityData().set(seatAccessor, Optional.empty());
                    }
                }

                else {
                    Entity passenger = whale.getEntityByUUID(uuid);
                    if (passenger != null && getSeatByEntity(passenger) != seatIndex) {
                        whale.getEntityData().set(seatAccessor, Optional.empty());
                    }
                }
            }
        }
    }

    public void assignSeat(int seatIndex, @Nullable Entity passenger) {
        if (seatIndex < 0 || seatIndex >= seatAccessors.length) return;
        
        if (passenger == null) {
            setSeatData(seatIndex, Optional.empty());
        } else {
            whale.getEntityData().set(getSeatAccessor(seatIndex), Optional.of(passenger.getUUID()));
            if (whale.level() instanceof net.minecraft.server.level.ServerLevel) {
                ((net.minecraft.server.level.ServerLevel) whale.level()).getChunkSource().broadcast(
                    whale, 
                    new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(whale)
                );
            }
        }
    }

    public int getSeatByEntity(Entity entity) {
        if (entity == null) return -1;
        UUID uuid = entity.getUUID();
        for (int i = 0; i < seatAccessors.length; i++) {
            Optional<UUID> seat = getSeatData(i);
            if (seat.isPresent() && seat.get().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    public int findFreeSeat() {
         for (int i = 0; i < seatAccessors.length; i++) {
             if (getSeatData(i).isEmpty()) return i;
         }
         return -1;
    }

    public boolean isPassengerAssigned(Entity passenger) {
        return getSeatByEntity(passenger) != -1;
    }

    public void removePassenger(Entity passenger) {
        int seatIndex = getSeatByEntity(passenger);
        if (seatIndex != -1) {
            assignSeat(seatIndex, null);
        }
    }

    public Optional<Entity> getPassengerForSeat(int seatIndex) {
        if (seatIndex < 0 || seatIndex >= seatAccessors.length) return Optional.empty();
        
        Optional<UUID> uuid = getSeatData(seatIndex);
        if (uuid.isEmpty()) return Optional.empty();
        
        if (whale.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid.get());
            return Optional.ofNullable(entity);
        }
        return Optional.empty();
    }
    public void rotatePassengers() {
        if (whale.getPartManager() == null) return;
        
        var partManager = whale.getPartManager();

        for (Entity passenger : whale.getPassengers()) {
            int seat = getSeatByEntity(passenger);
            int partIndex;
            float offset = 0;
            if (seat == 0 || seat == 1) {
                partIndex = 0;
            } else if (seat == 2 || seat == 4) {
                partIndex = 2;
                offset = -1;
            } else if (seat == 3 || seat == 5) {
                partIndex = 2;
                offset = 1;
            } else if (seat == 6) {
                partIndex = 4;
            } else {
                continue;
            }

            if (!(passenger instanceof net.minecraft.world.entity.player.Player)) {
                if(!(passenger instanceof com.fruityspikes.whaleborne.server.entities.CannonEntity cannonEntity && cannonEntity.isVehicle())){
                    if(passenger instanceof com.fruityspikes.whaleborne.server.entities.SailEntity){
                        passenger.yRotO = (net.minecraft.util.Mth.rotLerp((float) (0.05 + 0.1 * partIndex), passenger.getYRot(), partManager.oldPartYRot[partIndex]) + offset);
                        passenger.xRotO = (net.minecraft.util.Mth.rotLerp((float) (0.05 + 0.1 * partIndex), passenger.getXRot(), partManager.oldPartXRot[partIndex]));
                        passenger.setYRot(net.minecraft.util.Mth.rotLerp((float) (0.05 + 0.1 * partIndex), passenger.getYRot(), partManager.partYRot[partIndex]) + offset);
                        passenger.setXRot(net.minecraft.util.Mth.rotLerp((float) (0.05 + 0.1 * partIndex), passenger.getXRot(), partManager.partXRot[partIndex]));
                    }
                    else {
                        passenger.yRotO = partManager.oldPartYRot[partIndex];
                        passenger.xRotO = partManager.oldPartXRot[partIndex];
                        passenger.setYRot((partManager.partYRot[partIndex]) + offset);
                        passenger.setXRot(partManager.partXRot[partIndex]);
                    }
                }
            }
        }
    }
}
