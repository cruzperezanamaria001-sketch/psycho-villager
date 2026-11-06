package com.psycho.villager.logic;

import com.psycho.villager.data.InvestigationData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** El libro "Registro de investigaciones": se actualiza solo cada vez que lo abres. */
public final class InvestigationLog {
    private InvestigationLog() {}

    public static final String TAG = "PsychoLog";

    public static boolean isLog(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) && stack.hasTag() && stack.getTag().getBoolean(TAG);
    }

    public static ItemStack create(ServerLevel level) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "Registro de investigaciones");
        tag.putString("author", "Investigador");
        tag.putBoolean(TAG, true);
        fill(level, tag);
        return book;
    }

    /** Vuelve a escribir las paginas con lo ultimo descubierto. */
    public static void refresh(ServerLevel level, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.remove("resolved");
        fill(level, tag);
    }

    private static void fill(ServerLevel level, CompoundTag tag) {
        ListTag pages = new ListTag();
        for (String page : InvestigationData.get(level).buildPages(level.getDayTime())) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        }
        tag.put("pages", pages);
    }
}
