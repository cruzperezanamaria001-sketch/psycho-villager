package com.psycho.villager.data;

import com.psycho.villager.logic.Describe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cuaderno de la investigacion (guardado en el mundo): casos, testigos interrogados y pistas.
 * Solo se apunta lo que el jugador descubre al interrogar; las memorias reales viven en cada aldeano.
 */
public class InvestigationData extends SavedData {
    private static final String NAME = "psychovillager_cases";
    private static final int MAX_CRIMES = 40;
    private static final int MAX_ALIBIS = 40;
    /** Lineas que caben en una pagina del libro (cada linea larga cuenta como varias). */
    private static final int PAGE_LINES = 13;
    private static final int LINE_WIDTH = 18;

    public static final class Crime {
        public int id;
        public String victim = "";
        public int x;
        public int y;
        public int z;
        public long day;
        public int time;
        public final List<String> witnesses = new ArrayList<>();
        public final List<String> clues = new ArrayList<>();
    }

    private final List<Crime> crimes = new ArrayList<>();
    private final List<String> alibis = new ArrayList<>();
    private final Set<UUID> bookGiven = new HashSet<>();
    private int nextId = 1;

    public static InvestigationData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(InvestigationData::load, InvestigationData::new, NAME);
    }

    public static InvestigationData load(CompoundTag tag) {
        InvestigationData data = new InvestigationData();
        data.nextId = Math.max(1, tag.getInt("nextId"));
        ListTag list = tag.getList("crimes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Crime crime = new Crime();
            crime.id = c.getInt("id");
            crime.victim = c.getString("victim");
            crime.x = c.getInt("x");
            crime.y = c.getInt("y");
            crime.z = c.getInt("z");
            crime.day = c.getLong("day");
            crime.time = c.getInt("time");
            readStrings(c.getList("witnesses", Tag.TAG_STRING), crime.witnesses);
            readStrings(c.getList("clues", Tag.TAG_STRING), crime.clues);
            data.crimes.add(crime);
        }
        readStrings(tag.getList("alibis", Tag.TAG_STRING), data.alibis);
        ListTag given = tag.getList("bookGiven", Tag.TAG_STRING);
        for (int i = 0; i < given.size(); i++) {
            try {
                data.bookGiven.add(UUID.fromString(given.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // se ignora
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextId", this.nextId);
        ListTag list = new ListTag();
        for (Crime crime : this.crimes) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", crime.id);
            c.putString("victim", crime.victim);
            c.putInt("x", crime.x);
            c.putInt("y", crime.y);
            c.putInt("z", crime.z);
            c.putLong("day", crime.day);
            c.putInt("time", crime.time);
            c.put("witnesses", writeStrings(crime.witnesses));
            c.put("clues", writeStrings(crime.clues));
            list.add(c);
        }
        tag.put("crimes", list);
        tag.put("alibis", writeStrings(this.alibis));
        ListTag given = new ListTag();
        for (UUID id : this.bookGiven) {
            given.add(StringTag.valueOf(id.toString()));
        }
        tag.put("bookGiven", given);
        return tag;
    }

    private static void readStrings(ListTag source, List<String> target) {
        for (int i = 0; i < source.size(); i++) {
            target.add(source.getString(i));
        }
    }

    private static ListTag writeStrings(List<String> source) {
        ListTag list = new ListTag();
        for (String s : source) {
            list.add(StringTag.valueOf(s));
        }
        return list;
    }

    // ------------------------------------------------------------------ casos

    public int addCrime(String victim, BlockPos pos, long day, int time) {
        Crime crime = new Crime();
        crime.id = this.nextId++;
        crime.victim = victim;
        crime.x = pos.getX();
        crime.y = pos.getY();
        crime.z = pos.getZ();
        crime.day = day;
        crime.time = time;
        this.crimes.add(crime);
        while (this.crimes.size() > MAX_CRIMES) {
            this.crimes.remove(0);
        }
        this.setDirty();
        return crime.id;
    }

    private Crime find(int id) {
        for (Crime crime : this.crimes) {
            if (crime.id == id) {
                return crime;
            }
        }
        return null;
    }

    public void addWitness(int crimeId, String name) {
        Crime crime = find(crimeId);
        if (crime != null && !crime.witnesses.contains(name)) {
            crime.witnesses.add(name);
            this.setDirty();
        }
    }

    public void addClue(int crimeId, String text) {
        Crime crime = find(crimeId);
        if (crime != null && !crime.clues.contains(text)) {
            crime.clues.add(text);
            this.setDirty();
        }
    }

    public void addAlibi(String text) {
        if (!this.alibis.contains(text)) {
            this.alibis.add(text);
            while (this.alibis.size() > MAX_ALIBIS) {
                this.alibis.remove(0);
            }
            this.setDirty();
        }
    }

    /** true la primera vez para ese jugador (se le entrega el libro). */
    public boolean markBookGiven(UUID player) {
        boolean added = this.bookGiven.add(player);
        if (added) {
            this.setDirty();
        }
        return added;
    }

    // ------------------------------------------------------------------ libro

    /** Paginas del libro "Registro de investigaciones" con lo descubierto hasta ahora. */
    public List<String> buildPages(long nowDayTime) {
        PageBuilder pages = new PageBuilder();
        pages.line("REGISTRO DE");
        pages.line("INVESTIGACIONES");
        pages.line("");
        pages.line("Casos: " + this.crimes.size());
        pages.line("");
        pages.line("Aquí se apunta lo que descubres al interrogar a los aldeanos.");
        pages.line("");
        pages.line("Usa agachado + clic derecho sobre un aldeano para interrogarlo.");

        if (this.crimes.isEmpty()) {
            pages.flush();
            pages.line("Todavía no hay casos.");
        }
        for (Crime crime : this.crimes) {
            pages.flush();
            pages.line("CASO #" + crime.id);
            pages.line("Víctima: " + crime.victim);
            pages.line("Lugar: cerca de (" + crime.x + ", " + crime.y + ", " + crime.z + ")");
            pages.line("Cuándo: " + Describe.when(nowDayTime, crime.day, crime.time));
            pages.line("");
            if (crime.witnesses.isEmpty()) {
                pages.line("Testigos: ninguno todavía");
            } else {
                pages.line("Testigos:");
                for (String w : crime.witnesses) {
                    pages.line("- " + w);
                }
            }
            if (!crime.clues.isEmpty()) {
                pages.line("");
                pages.line("Pistas:");
                for (String clue : crime.clues) {
                    pages.line("- " + clue);
                }
            }
        }
        if (!this.alibis.isEmpty()) {
            pages.flush();
            pages.line("COARTADAS");
            pages.line("(dónde dijeron que estaban)");
            pages.line("");
            for (String alibi : this.alibis) {
                pages.line("- " + alibi);
            }
        }
        pages.flush();
        return pages.pages;
    }

    private static final class PageBuilder {
        final List<String> pages = new ArrayList<>();
        private final StringBuilder current = new StringBuilder();
        private int used;

        void line(String text) {
            int width = Math.max(1, (text.length() + LINE_WIDTH - 1) / LINE_WIDTH);
            if (this.used + width > PAGE_LINES) {
                flush();
            }
            this.current.append(text).append('\n');
            this.used += width;
        }

        void flush() {
            if (this.current.length() > 0) {
                this.pages.add(this.current.toString());
                this.current.setLength(0);
                this.used = 0;
            }
        }
    }
}
