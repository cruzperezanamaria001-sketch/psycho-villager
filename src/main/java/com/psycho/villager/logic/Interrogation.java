package com.psycho.villager.logic;

import com.psycho.villager.Config;
import com.psycho.villager.data.InvestigationData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Interrogar aldeanos: un menu en el chat con 4 preguntas (se pulsan con el raton).
 * Las respuestas salen de lo que el aldeano realmente vio. El asesino miente.
 */
public final class Interrogation {
    private Interrogation() {}

    private static final String[] QUESTIONS = {
            "¿Viste algo?",
            "¿Dónde estabas anoche?",
            "¿Viste a alguien sospechoso?",
            "¿Quién crees que es el asesino?"
    };

    public static void openMenu(ServerPlayer player, Villager villager) {
        ServerLevel level = (ServerLevel) player.level();
        String name = VillagerNames.nameOf(villager);

        player.sendSystemMessage(Component.literal("— Interrogando a " + name + " —")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (int i = 0; i < QUESTIONS.length; i++) {
            String command = "/psycho ask " + villager.getUUID() + " " + (i + 1);
            player.sendSystemMessage(Component.literal(" ➤ " + QUESTIONS[i]).withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Preguntar a " + name)))));
        }
        if (Config.INTERROGATE_REQUIRES_SNEAK) {
            player.sendSystemMessage(Component.literal("(Agachado + clic derecho interroga; clic derecho normal comercia.)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        // La primera vez que interrogas a alguien te dan el libro de la investigacion.
        if (InvestigationData.get(level).markBookGiven(player.getUUID())) {
            ItemStack book = InvestigationLog.create(level);
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
            player.sendSystemMessage(Component.literal("Has recibido el Registro de investigaciones. Ábrelo con clic derecho.")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void answer(ServerPlayer player, Villager villager, int question) {
        if (!villager.isAlive() || player.distanceToSqr(villager) > 100.0D) {
            player.sendSystemMessage(Component.literal("Está demasiado lejos para hablar con él.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        player.sendSystemMessage(Component.literal("Tú: " + QUESTIONS[question - 1]).withStyle(ChatFormatting.GRAY));

        switch (question) {
            case 1 -> askSeen(level, player, villager);
            case 2 -> askNight(level, player, villager);
            case 3 -> askSuspect(level, player, villager);
            default -> askKiller(level, player, villager);
        }
    }

    // ------------------------------------------------------------------ preguntas

    private static void askSeen(ServerLevel level, ServerPlayer player, Villager villager) {
        String name = VillagerNames.nameOf(villager);
        if (PsychoManager.isKiller(villager)) {
            say(player, name, "No, no vi nada. Yo estaba a lo mío.");
            return;
        }
        List<CompoundTag> memories = WitnessSystem.memories(villager);
        if (memories.isEmpty()) {
            say(player, name, "No, no he visto nada raro.");
            return;
        }
        InvestigationData data = InvestigationData.get(level);
        int shown = 0;
        for (CompoundTag m : memories) {
            if (shown++ >= 3) {
                break;
            }
            String when = Describe.when(level.getDayTime(), m.getLong("Day"), m.getInt("Time"));
            String where = Describe.where(villager.position(), m.getInt("X"), m.getInt("Y"), m.getInt("Z"));
            String text = "Sí... vi cómo atacaban a " + m.getString("Victim") + " (" + when + "), " + where + ".";
            if (!m.getBoolean("Identified")) {
                text += " Pero no pude ver quién fue.";
            }
            say(player, name, text);
            data.addWitness(m.getInt("Crime"), name);
            data.addClue(m.getInt("Crime"), name + " vio el ataque a " + m.getString("Victim") + " (" + when + ").");
        }
    }

    private static void askNight(ServerLevel level, ServerPlayer player, Villager villager) {
        String name = VillagerNames.nameOf(villager);
        if (PsychoManager.isKiller(villager)) {
            say(player, name, "Estaba durmiendo en mi cama, en casa. Pregunta a quien quieras.");
            return;
        }
        CompoundTag night = villager.getPersistentData().getCompound("PsychoNight");
        if (night.isEmpty()) {
            say(player, name, "No me acuerdo bien... no estaba pendiente.");
            return;
        }
        int x = night.getInt("X");
        int y = night.getInt("Y");
        int z = night.getInt("Z");
        String when = Describe.when(level.getDayTime(), night.getLong("Day"), Config.NIGHT_SNAPSHOT_TICK);
        say(player, name, when.substring(0, 1).toUpperCase() + when.substring(1) + " estaba "
                + Describe.where(villager.position(), x, y, z) + ".");
        InvestigationData.get(level).addAlibi(name + ": " + when + " estaba cerca de (" + x + ", " + y + ", " + z + ").");
    }

    private static void askSuspect(ServerLevel level, ServerPlayer player, Villager villager) {
        String name = VillagerNames.nameOf(villager);
        if (PsychoManager.isKiller(villager)) {
            String other = randomOtherName(level, villager);
            if (other != null && level.random.nextBoolean()) {
                say(player, name, "No vi a nadie sospechoso... aunque últimamente " + other + " anda muy raro.");
            } else {
                say(player, name, "No, nadie sospechoso por aquí.");
            }
            return;
        }
        List<CompoundTag> memories = WitnessSystem.memories(villager);
        if (memories.isEmpty()) {
            say(player, name, "No, no vi a nadie sospechoso.");
            return;
        }
        CompoundTag m = memories.get(0);
        String clothes = m.getString("Clothes");
        String escape = m.getString("Escape");
        boolean identified = m.getBoolean("Identified");
        int crime = m.getInt("Crime");
        InvestigationData data = InvestigationData.get(level);

        if (!identified && clothes.isEmpty()) {
            say(player, name, "Vi el ataque, pero no pude ver quién lo hizo.");
            data.addWitness(crime, name);
            data.addClue(crime, name + " no pudo ver al atacante.");
            return;
        }

        StringBuilder text = new StringBuilder();
        StringBuilder clue = new StringBuilder(name);
        if (identified) {
            text.append("Sí. Vi a ").append(m.getString("Killer"));
            clue.append(" vio a ").append(m.getString("Killer"));
            if (!clothes.isEmpty()) {
                text.append(", con ropa de color ").append(clothes);
                clue.append(" (ropa ").append(clothes).append(')');
            }
        } else {
            text.append("Vi a alguien con ropa de color ").append(clothes).append(", pero no pude verle la cara");
            clue.append(" vio a alguien con ropa ").append(clothes).append(" (sin ver su cara)");
        }
        if (!escape.isEmpty()) {
            text.append(", y se fue hacia el ").append(escape);
            clue.append(", huyó hacia el ").append(escape);
        }
        text.append('.');
        clue.append('.');
        say(player, name, text.toString());
        data.addWitness(crime, name);
        data.addClue(crime, clue.toString());

        // Encontrar pruebas contra el asesino aumenta su sospecha (solo la primera vez por testimonio).
        if (identified && !m.getBoolean("Revealed")) {
            m.putBoolean("Revealed", true);
            for (PsychoManager.KillerState state : PsychoManager.allKillers()) {
                if (m.getString("Killer").equals(VillagerNames.nameOf(state.villager))) {
                    Innocence.onEvidence(state, Config.EVIDENCE_SUSPICION);
                }
            }
        }
    }

    private static void askKiller(ServerLevel level, ServerPlayer player, Villager villager) {
        String name = VillagerNames.nameOf(villager);
        if (PsychoManager.isKiller(villager)) {
            String other = randomOtherName(level, villager);
            if (other != null && level.random.nextBoolean()) {
                say(player, name, "Yo no fui, desde luego... ¿has pensado en " + other + "?");
            } else {
                say(player, name, "No lo sé... yo no fui, eso seguro.");
            }
            return;
        }
        List<CompoundTag> memories = WitnessSystem.memories(villager);
        if (memories.isEmpty()) {
            say(player, name, "No lo sé. No vi nada.");
            return;
        }
        CompoundTag m = memories.get(0);
        int crime = m.getInt("Crime");
        InvestigationData data = InvestigationData.get(level);
        data.addWitness(crime, name);
        if (m.getBoolean("Identified")) {
            say(player, name, "Estoy seguro: fue " + m.getString("Killer") + ".");
            data.addClue(crime, name + " acusa a " + m.getString("Killer") + ".");
            if (!m.getBoolean("Revealed")) {
                m.putBoolean("Revealed", true);
                for (PsychoManager.KillerState state : PsychoManager.allKillers()) {
                    if (m.getString("Killer").equals(VillagerNames.nameOf(state.villager))) {
                        Innocence.onEvidence(state, Config.EVIDENCE_SUSPICION);
                    }
                }
            }
        } else {
            say(player, name, "No lo sé... no pude ver quién fue.");
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static void say(ServerPlayer player, String name, String text) {
        player.sendSystemMessage(Component.literal("<" + name + "> " + text));
    }

    private static String randomOtherName(ServerLevel level, Villager self) {
        List<Villager> others = level.getEntitiesOfClass(Villager.class, self.getBoundingBox().inflate(48.0D),
                v -> v != self && v.isAlive() && v.hasCustomName());
        if (others.isEmpty()) {
            return null;
        }
        return others.get(level.random.nextInt(others.size())).getCustomName().getString();
    }
}
