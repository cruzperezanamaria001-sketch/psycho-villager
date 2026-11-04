package com.psycho.villager.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Dato guardado del mundo: si ya se eligio un aldeano asesino (para no elegir otro cada noche). */
public class PsychoData extends SavedData {
    private static final String NAME = "psychovillager_data";

    private boolean killerChosen;

    public static PsychoData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(PsychoData::load, PsychoData::new, NAME);
    }

    public static PsychoData load(CompoundTag tag) {
        PsychoData data = new PsychoData();
        data.killerChosen = tag.getBoolean("killerChosen");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("killerChosen", this.killerChosen);
        return tag;
    }

    public boolean isKillerChosen() {
        return this.killerChosen;
    }

    public void setKillerChosen(boolean value) {
        this.killerChosen = value;
        this.setDirty();
    }
}
