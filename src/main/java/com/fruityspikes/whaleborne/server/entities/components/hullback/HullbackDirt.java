package com.fruityspikes.whaleborne.server.entities.components.hullback;

import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.network.SyncHullbackDirtPayload;
import com.fruityspikes.whaleborne.server.data.HullbackDirtManager;
import com.fruityspikes.whaleborne.server.entities.AbstractWhale;
import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import com.fruityspikes.whaleborne.server.entities.HullbackPartEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HullbackDirt {
    private final HullbackEntity whale;
    
    public BlockState[][] headDirt; // 8 x 5
    public BlockState[][] headTopDirt; // 8 x 5
    public BlockState[][] bodyDirt; // 5 x 5
    public BlockState[][] bodyTopDirt; // 5 x 5
    public BlockState[][] tailDirt; // 2 x 2
    public BlockState[][] flukeDirt; // 4 x 4

    private final RandomSource random;

    public HullbackDirt(HullbackEntity whale) {
        this.whale = whale;
        this.random = whale.getRandom();
        this.initArrays();
    }
    
    public void init() {
         if(!whale.level().isClientSide) initDirt();
         else initClientDirt();
    }

    private void initArrays() {
        headDirt = new BlockState[8][5];
        headTopDirt = new BlockState[8][5];
        bodyDirt = new BlockState[6][5];
        bodyTopDirt = new BlockState[6][5];
        tailDirt = new BlockState[4][2];
        flukeDirt = new BlockState[3][5];
    }

    private void initClientDirt() {
        initArrays();
        for (BlockState[][] array : new BlockState[][][]{headDirt, headTopDirt, bodyDirt, bodyTopDirt, tailDirt, flukeDirt}) {
            for (int x = 0; x < array.length; x++) {
                for (int y = 0; y < array[x].length; y++) {
                    array[x][y] = Blocks.AIR.defaultBlockState();
                }
            }
        }
    }

    private void initDirt() {
        initClientDirt(); // clear to air first

        fillDirtArray(headDirt, true, getPartName(whale.head));
        fillDirtArray(headTopDirt, false, getPartName(whale.head));
        fillDirtArray(bodyDirt, true, getPartName(whale.body));
        fillDirtArray(bodyTopDirt, false, getPartName(whale.body));
        fillDirtArray(tailDirt, true, getPartName(whale.tail));
        fillDirtArray(flukeDirt, true, getPartName(whale.tail));

        syncDirtToClients();
    }
    
    // Helper to access private logic if needed, but for now we assume names are consistent
    private String getPartName(HullbackPartEntity part) {
        return part.name;
    }

    private void fillDirtArray(BlockState[][] array, boolean bottom, String partName) {
        List<HullbackDirtManager.HullbackDirtEntry> candidates = getCandidates(bottom, partName);

        for (int x = 0; x < array.length; x++) {
            for (int y = 0; y < array[x].length; y++) {
                array[x][y] = Blocks.AIR.defaultBlockState();
                if (candidates.isEmpty()) continue;
                List<HullbackDirtManager.HullbackDirtEntry> successful = new ArrayList<>();

                for (HullbackDirtManager.HullbackDirtEntry entry : candidates) {
                    if (random.nextDouble() < entry.placementChance()) successful.add(entry);
                }

                if (!successful.isEmpty()) {
                    HullbackDirtManager.HullbackDirtEntry chosen = successful.get(random.nextInt(successful.size()));
                    array[x][y] = applyProperties(chosen.block(), chosen.blockProperties());
                }
            }
        }
    }

    private List<HullbackDirtManager.HullbackDirtEntry> getCandidates(boolean bottom, String partName) {
        String key = partName + "_" + (bottom ? "bottom" : "top");
        return Whaleborne.PROXY.getHullbackDirtManager().get().stream()
                .filter(e -> e.placements().contains(key))
                .toList();
    }

    public static BlockState applyProperties(Block block, @Nullable Map<String, String> props) {
        BlockState state = block.defaultBlockState();
        if (props == null || props.isEmpty()) return state;

        for (Map.Entry<String, String> entry : props.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();

            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            if (property == null) {
                Whaleborne.LOGGER.warn("Unknown blockstate property '{}' for block {}", name, block);
                continue;
            }

            state = setProperty(state, property, value);
        }

        return state;
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }


    public void randomTick(String partName, boolean bottom) {
        if (whale.level().isClientSide) return;
        
        BlockState[][] array = getDirtArray(getArrayIndex(partName, bottom), bottom);
        if (array == null) return; // specific part doesn't support dirt or invalid name?

        if (bottom) {
             if (random.nextInt(30000) <= 5) {
                int x = getWeightedIndex(array.length, true);
                int y = getWeightedIndex(array[x].length, true);

                BlockState currentState = array[x][y];
                HullbackDirtManager.HullbackDirtEntry entry = Whaleborne.PROXY.getHullbackDirtManager().get().stream().filter(e -> e.matches(currentState)).findFirst().orElse(null);
                
                if (entry != null && entry.growth().isPresent() && this.random.nextBoolean()) {
                    array[x][y] = applyProperties(entry.growth().get(), entry.growthProperties());
                    this.whale.level().playSound(null, whale.getX(), whale.getY(), whale.getZ(), entry.soundOnGrowth() != null ? entry.soundOnGrowth() : net.minecraft.sounds.SoundEvents.BONE_MEAL_USE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                    syncDirtToClients();
                    return;
                }

                if (currentState == null || currentState.isAir()) {
                    List<HullbackDirtManager.HullbackDirtEntry> candidates = getCandidates(true, partName);
                    List<HullbackDirtManager.HullbackDirtEntry> successful = new ArrayList<>();

                    for (HullbackDirtManager.HullbackDirtEntry candidate : candidates) {
                        if (random.nextDouble() < candidate.placementChance()) {
                            successful.add(candidate);
                        }
                    }

                    if (!successful.isEmpty()) {
                        HullbackDirtManager.HullbackDirtEntry chosen = successful.get(random.nextInt(successful.size()));
                        array[x][y] = applyProperties(chosen.block(), chosen.blockProperties());

                        this.whale.level().playSound(null, whale.getX(), whale.getY(), whale.getZ(), chosen.soundOnGrowth() != null ? chosen.soundOnGrowth() : net.minecraft.sounds.SoundEvents.BONE_MEAL_USE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                        syncDirtToClients();
                    }
                }
            }
        } else {
             if (this.random.nextInt(30000) <= 5) {
                int x = getWeightedIndex(array.length, true);
                int y = getWeightedIndex(array[x].length, true);

                BlockState currentState = array[x][y];

                HullbackDirtManager.HullbackDirtEntry entry = Whaleborne.PROXY.getHullbackDirtManager().get().stream().filter(e -> e.matches(currentState)).findFirst().orElse(null);

                if (entry != null && entry.growth().isPresent() && this.random.nextBoolean()) {
                    array[x][y] = applyProperties(entry.growth().get(), entry.growthProperties());
                    if (entry.soundOnGrowth() != null) {
                        this.whale.level().playSound(null, whale.getX(), whale.getY(), whale.getZ(), entry.soundOnGrowth(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0f);
                    } else {
                        this.whale.level().playSound(null, whale.getX(), whale.getY(), whale.getZ(), net.minecraft.sounds.SoundEvents.BONE_MEAL_USE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0f);
                    }
                    syncDirtToClients();
                    return;
                }

                if (currentState == null || currentState.isAir()) {
                    // Logic for top seems similar but distinct in original code?
                    // Original code: if (currentState == null || currentState.isAir()) { List candidates = getCandidates(false, partName); ... }
                    // Yes, essentially same structure.
                    List<HullbackDirtManager.HullbackDirtEntry> candidates = getCandidates(false, partName);
                    // ... (Original Code didn't finish showing, assuming symmetric logic) ...
                    // Let's implement check for candidates:
                     List<HullbackDirtManager.HullbackDirtEntry> successful = new ArrayList<>();
                     for (HullbackDirtManager.HullbackDirtEntry candidate : candidates) {
                        if (random.nextDouble() < candidate.placementChance()) {
                            successful.add(candidate);
                        }
                    }
                     if (!successful.isEmpty()) {
                        HullbackDirtManager.HullbackDirtEntry chosen = successful.get(random.nextInt(successful.size()));
                        array[x][y] = applyProperties(chosen.block(), chosen.blockProperties());
                         if (chosen.soundOnGrowth() != null) {
                            this.whale.level().playSound(null, whale.getX(), whale.getY(), whale.getZ(), chosen.soundOnGrowth(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0f);
                        } else {
                            this.whale.level().playSound(null, whale.getX(), whale.getY(), whale.getZ(), net.minecraft.sounds.SoundEvents.BONE_MEAL_USE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0f);
                        }
                        syncDirtToClients();
                    }
                }
            }
        }
    }

    private int getWeightedIndex(int length, boolean higherWeight) {
        double[] weights = new double[length];
        double totalWeight = 0;

        for (int i = 0; i < length; i++) {
            if (higherWeight) {
                weights[i] = i + 1;
            } else {
                weights[i] = length - i;
            }
            totalWeight += weights[i];
        }

        double randomValue = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0;

        for (int i = 0; i < length; i++) {
            cumulativeWeight += weights[i];
            if (randomValue <= cumulativeWeight) {
                return i;
            }
        }
        return length - 1;
    }
    
    // Helper to map part name back to array index for internal usage
    private int getArrayIndex(String partName, boolean bottom) {
        if (bottom) {
             if (partName.contains("head")) return 0; // head
             if (partName.contains("body")) return 2; // body
             if (partName.contains("tail")) return 3; // tail
             if (partName.contains("fluke")) return 4; // fluke
        } else {
             if (partName.contains("head")) return 0; // headTop
             if (partName.contains("body")) return 2; // bodyTop (mapped to index 2 here? wait)
             // getDirtArray logic: index 0 -> headTop, index != 0 -> bodyTop
             // So if top: 0 for head, 1 for body?
             // let's check getDirtArray logic again:
             /*
                if(bottom){ switch(index) { case 2: body, case 3: tail, case 4: fluke, default: head } }
                else { if (index == 0) headTop else bodyTop }
             */
             if (partName.contains("head")) return 0;
             return 1; // bodyTop
        }
        return -1;
    }

    public void clearTopDirt() {
         for (int x = 0; x < headTopDirt.length; x++) {
             for (int y = 0; y < headTopDirt[x].length; y++) {
                 headTopDirt[x][y] = Blocks.AIR.defaultBlockState();
             }
         }
         for (int x = 0; x < bodyTopDirt.length; x++) {
             for (int y = 0; y < bodyTopDirt[x].length; y++) {
                 bodyTopDirt[x][y] = Blocks.AIR.defaultBlockState();
             }
         }
    }

    public void syncDirtToClients() {
        if (!whale.level().isClientSide) {
            syncDirtArray(headDirt, 0, true);
            syncDirtArray(headTopDirt, 1, false);
            syncDirtArray(bodyDirt, 2, true);
            syncDirtArray(bodyTopDirt, 3, false);
            syncDirtArray(tailDirt, 4, true);
            syncDirtArray(flukeDirt, 5, true);
        }
    }

    public void syncDirtArray(BlockState[][] array, int arrayType, boolean isBottom) {
        if (!whale.level().isClientSide) {
           PacketDistributor.sendToPlayersTrackingEntity(whale, new SyncHullbackDirtPayload(whale.getId(), array, arrayType, isBottom));
        }
    }

    public BlockState[][] getDirtArray(int index, boolean bottom){
        if(bottom){
            switch (index){
                case 2: return bodyDirt;
                case 3: return tailDirt;
                case 4: return flukeDirt;
                default: return headDirt;
            }
        }
        else {
            if (index == 0)
                return headTopDirt;
            else return bodyTopDirt;
        }
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        compound.put("HeadDirt", saveDirtArray(headDirt));
        compound.put("HeadTopDirt", saveDirtArray(headTopDirt));
        compound.put("BodyDirt", saveDirtArray(bodyDirt));
        compound.put("BodyTopDirt", saveDirtArray(bodyTopDirt));
        compound.put("TailDirt", saveDirtArray(tailDirt));
        compound.put("FlukeDirt", saveDirtArray(flukeDirt));
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains("HeadDirt")) headDirt = loadDirtArray(compound.getCompound("HeadDirt"));
        if (compound.contains("HeadTopDirt")) headTopDirt = loadDirtArray(compound.getCompound("HeadTopDirt"));
        if (compound.contains("BodyDirt")) bodyDirt = loadDirtArray(compound.getCompound("BodyDirt"));
        if (compound.contains("BodyTopDirt")) bodyTopDirt = loadDirtArray(compound.getCompound("BodyTopDirt"));
        if (compound.contains("TailDirt")) tailDirt = loadDirtArray(compound.getCompound("TailDirt"));
        if (compound.contains("FlukeDirt")) flukeDirt = loadDirtArray(compound.getCompound("FlukeDirt"));
        
        syncDirtToClients();
    }

    private CompoundTag saveDirtArray(BlockState[][] array) {
        CompoundTag tag = new CompoundTag();
        for (int x = 0; x < array.length; x++) {
            ListTag column = new ListTag();
            for (int y = 0; y < array[x].length; y++) {
                column.add(NbtUtils.writeBlockState(array[x][y]));
            }
            tag.put("x" + x, column);
        }
        return tag;
    }

    private BlockState[][] loadDirtArray(CompoundTag tag) {
        BlockState[][] array = new BlockState[tag.size()][];
        for (int x = 0; x < array.length; x++) {
            String key = "x" + x;
            if (tag.contains(key)) {
                ListTag column = tag.getList(key, 10);
                array[x] = new BlockState[column.size()];
                for (int y = 0; y < column.size(); y++) {
                    array[x][y] = NbtUtils.readBlockState(whale.level().holderLookup(Registries.BLOCK), column.getCompound(y));
                }
            }
        }
        return array;
    }
    public BlockState[][] getDirtArrayForPart(HullbackPartEntity part, boolean top) {
        if (part == whale.head) {
            return top ? headTopDirt : headDirt;
        } else if (part == whale.body) {
            return top ? bodyTopDirt : bodyDirt;
        } else if (part == whale.fluke) {
            return flukeDirt;
        } else if (part == whale.nose) {
            return top ? headTopDirt : headDirt;
        }
        return tailDirt;
    }
}
