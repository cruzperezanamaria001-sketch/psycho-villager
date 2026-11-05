package com.psycho.villager.logic;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pone nombre a todos los aldeanos (sin repetir entre los que estan cargados). */
public final class VillagerNames {
    private VillagerNames() {}

    private static final String[] NAMES = {
            "Ana", "Luis", "Carlos", "Marta", "Pedro", "Lucía", "Javier", "Sofía", "Diego", "Elena",
            "Pablo", "Laura", "Miguel", "Carmen", "Andrés", "Rosa", "Tomás", "Isabel", "Sergio", "Julia",
            "Raúl", "Clara", "Hugo", "Irene", "Álvaro", "Paula", "Rubén", "Nuria", "Iván", "Alba",
            "Óscar", "Silvia", "Adrián", "Teresa", "Víctor", "Beatriz", "Mateo", "Inés", "Bruno", "Lola",
            "Emilio", "Pilar", "Gonzalo", "Marina", "Ángel", "Dolores", "Félix", "Aurora", "Ramón", "Celia",
            "Héctor", "Noemí", "Joaquín", "Esther", "Manuel", "Olga", "Fernando", "Vera", "Ignacio", "Rocío",
            "Gael", "Mónica", "Lorenzo", "Natalia", "Cristian", "Patricia", "Salvador", "Eva", "Benito", "Lidia",
            "Gustavo", "Sara", "Rodrigo", "Yolanda", "Esteban", "Amparo", "Nicolás", "Josefa", "Fabián", "Gloria",
            "Leandro", "Matilde", "Armando", "Consuelo", "Julián", "Soledad", "Ezequiel", "Fátima", "Alberto", "Daniela",
            "Martín", "Camila", "Santiago", "Valentina", "Samuel", "Renata", "Darío", "Ximena", "Gabriel", "Alicia"
    };

    /** Nombres de los aldeanos que estan cargados ahora mismo. */
    private static final Map<UUID, String> LOADED = new HashMap<>();

    /** Se llama cuando un aldeano entra al mundo: si no tiene nombre, se le pone uno. */
    public static void onJoin(Villager villager, RandomSource random) {
        if (villager.hasCustomName()) {
            LOADED.put(villager.getUUID(), villager.getCustomName().getString());
            return;
        }
        Set<String> used = new HashSet<>(LOADED.values());
        List<String> free = new ArrayList<>();
        for (String name : NAMES) {
            if (!used.contains(name)) {
                free.add(name);
            }
        }
        String chosen = free.isEmpty()
                ? NAMES[random.nextInt(NAMES.length)]
                : free.get(random.nextInt(free.size()));
        villager.setCustomName(Component.literal(chosen));
        LOADED.put(villager.getUUID(), chosen);
    }

    public static void onLeave(Entity entity) {
        LOADED.remove(entity.getUUID());
    }

    public static void reset() {
        LOADED.clear();
    }

    public static String nameOf(Entity entity) {
        return entity.hasCustomName() ? entity.getCustomName().getString() : "un aldeano";
    }
}
