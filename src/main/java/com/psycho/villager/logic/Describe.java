package com.psycho.villager.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Textos que usan los aldeanos para contar lo que vieron (direccion, hora, lugar, ropa). */
public final class Describe {
    private Describe() {}

    private static final String[] COMPASS = {
            "norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste"
    };

    /** Direccion cardinal: en Minecraft el norte es -Z y el este es +X. */
    public static String compass(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0.0D) {
            angle += 360.0D;
        }
        int index = (int) Math.round(angle / 45.0D) % 8;
        return COMPASS[index];
    }

    /** Parte del dia a partir de la hora del juego (0 = amanecer, 18000 = medianoche). */
    public static String partOfDay(int t) {
        if (t < 6000) {
            return "por la mañana";
        }
        if (t < 12000) {
            return "por la tarde";
        }
        if (t < 14000) {
            return "al atardecer";
        }
        if (t < 18000) {
            return "a primera hora de la noche";
        }
        if (t < 20000) {
            return "a medianoche";
        }
        if (t < 23000) {
            return "de madrugada";
        }
        return "al amanecer";
    }

    /** "anoche, a medianoche", "hace 3 dias, por la tarde"... */
    public static String when(long nowDayTime, long crimeDay, int crimeTime) {
        long nowDay = nowDayTime / 24000L;
        int nowTime = (int) (nowDayTime % 24000L);
        long diff = nowDay - crimeDay;
        boolean crimeAtNight = crimeTime >= 13000 && crimeTime < 23000;
        String part = partOfDay(crimeTime);
        if (diff <= 0) {
            return (crimeAtNight ? "esta noche" : "hoy") + ", " + part;
        }
        if (diff == 1) {
            return (crimeAtNight && nowTime < 13000 ? "anoche" : "ayer") + ", " + part;
        }
        return "hace " + diff + " días, " + part;
    }

    /** Lugar contado desde donde esta hablando el aldeano. */
    public static String where(Vec3 from, int x, int y, int z) {
        double dx = x + 0.5D - from.x;
        double dz = z + 0.5D - from.z;
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String coords = "(" + x + ", " + y + ", " + z + ")";
        if (dist < 6) {
            return "aquí cerca, en " + coords;
        }
        return "a unos " + dist + " bloques al " + compass(dx, dz) + " de aquí, en " + coords;
    }

    /** Color aproximado de la ropa segun el oficio del aldeano. */
    public static String clothes(Villager villager) {
        ResourceLocation key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(villager.getVillagerData().getProfession());
        String profession = key == null ? "none" : key.getPath();
        return switch (profession) {
            case "armorer", "toolsmith", "weaponsmith" -> "gris oscuro";
            case "butcher", "librarian" -> "blanco";
            case "cartographer" -> "marrón claro";
            case "cleric" -> "morado";
            case "farmer" -> "marrón y verde";
            case "fisherman" -> "azul grisáceo";
            case "fletcher" -> "marrón verdoso";
            case "leatherworker" -> "marrón oscuro";
            case "mason" -> "gris claro";
            case "nitwit" -> "verde";
            case "shepherd" -> "marrón y blanco";
            default -> "marrón";
        };
    }
}
