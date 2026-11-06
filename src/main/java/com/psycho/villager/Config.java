package com.psycho.villager;

/**
 * Valores del mod. Cambia lo que quieras aqui y vuelve a compilar.
 */
public final class Config {
    private Config() {}

    /** Tick del dia en que empieza la noche (13000 = anochecer). */
    public static final long NIGHT_START = 13000L;
    /** Tick del dia en que termina la noche (23000 = amanecer). */
    public static final long NIGHT_END = 23000L;

    /** Cuantos aldeanos mata el asesino cada noche. */
    public static final int MAX_KILLS_PER_NIGHT = 1;

    /** Si es true, al anochecer elige solo a un aldeano como asesino (una sola vez por mundo). */
    public static final boolean AUTO_CHOOSE_KILLER = true;

    /** Radio (en bloques) en el que busca victimas. */
    public static final double HUNT_RANGE = 64.0D;

    /** Velocidad al perseguir aldeanos / jugadores (1.0 = velocidad normal de aldeano base x2). */
    public static final double SPEED_VILLAGER = 0.6D;
    public static final double SPEED_PLAYER = 0.7D;

    /** Vida maxima del asesino (un aldeano normal tiene 20). */
    public static final double KILLER_HEALTH = 40.0D;

    /** Dano a jugadores por golpe y espera entre golpes (ticks). */
    public static final float PLAYER_DAMAGE = 6.0F;
    public static final int PLAYER_HIT_COOLDOWN = 25;

    /** Probabilidad de que el aldeano asesinado grite (0.0 a 1.0). */
    public static final float SCREAM_CHANCE = 0.8F;

    /** Cuanto duran los cadaveres en el mundo (ticks). 24000 = 1 dia de Minecraft. */
    public static final int CORPSE_LIFETIME = 24000 * 5;

    /** Cuantas manchas de sangre se dejan alrededor de un cadaver. */
    public static final int BLOOD_SPLATTERS = 12;

    /** Probabilidad de que el asesino esconda el cuerpo debajo de una cama (si hay una cerca). */
    public static final float HIDE_BODY_CHANCE = 0.4F;
    /** Radio (en bloques) en el que busca una cama para esconder el cuerpo. */
    public static final int HIDE_BODY_RADIUS = 12;

    /** Probabilidad de que, al empezar a cazar, el asesino vaya primero a una ventana (si hay una cerca de la victima). */
    public static final float WINDOW_CHANCE = 0.3F;
    /** Radio (en bloques) alrededor de la victima donde busca ventanas. */
    public static final int WINDOW_SEARCH_RADIUS = 10;
    /** Ticks que se queda mirando la ventana antes de romperla (20 = 1 segundo). */
    public static final int WINDOW_LOOK_TICKS = 40;

    /** Ticks que deja un rastro de sangre por donde camina despues de matar. */
    public static final int BLOOD_TRAIL_TICKS = 400;

    // ---------------------------------------------------------------- v1.1.0: testigos

    /** Hasta donde (bloques) puede un aldeano ver el asesinato como testigo. */
    public static final double WITNESS_RANGE = 32.0D;
    /** Hasta donde (bloques) puede reconocer al asesino. Mas lejos: ve algo, pero no sabe quien fue. */
    public static final double IDENTIFY_RANGE = 16.0D;
    /** Probabilidad de que un aldeano que lo ve se convierta en testigo. */
    public static final float WITNESS_CHANCE = 0.6F;
    /** Cuantos asesinatos recuerda cada testigo como maximo. */
    public static final int MAX_MEMORIES = 5;
    /** true: agachado + clic derecho interroga (clic normal sigue comerciando). false: clic derecho siempre interroga. */
    public static final boolean INTERROGATE_REQUIRES_SNEAK = true;
    /** Hora del dia (ticks) en que se apunta donde esta cada aldeano para "¿Donde estabas anoche?". */
    public static final int NIGHT_SNAPSHOT_TICK = 15000;

    // ---------------------------------------------------------------- v1.1.0: inocencia

    /** Cada noche el asesino empieza a cazar pasado un tiempo al azar (ticks despues del anochecer). */
    public static final int HUNT_DELAY_MIN = 600;
    public static final int HUNT_DELAY_MAX = 6000;
    /** A que distancia (bloques) cuenta que un jugador lo esta mirando. */
    public static final double WATCH_RANGE = 24.0D;
    /** Ticks mirandolo seguidos hasta que sube la sospecha. */
    public static final int GAZE_THRESHOLD = 60;
    /** Ticks con un jugador pegado a el hasta que sube la sospecha. */
    public static final int FOLLOW_THRESHOLD = 200;
    /** Cuanto sube la sospecha cuando un testigo lo acusa. */
    public static final int EVIDENCE_SUSPICION = 15;
    /** Niveles de sospecha (0 a 100): evita al jugador / cambia de zona / esconde pruebas. */
    public static final int SUSPICION_AVOID = 30;
    public static final int SUSPICION_ZONE = 50;
    public static final int SUSPICION_HIDE = 70;
    /** Si lo miran mientras va a matar, espera como mucho esto (ticks) antes de atacar igualmente. */
    public static final int WATCHED_MAX_DELAY = 600;

    /** Probabilidad de que el asesino suelte una lista de nombres despues de matar. */
    public static final float LIST_DROP_CHANCE = 0.35F;
    /** Si es false, el nombre del asesino NO sale en la lista (pista para encontrarlo). */
    public static final boolean LIST_INCLUDES_KILLER = false;
}
