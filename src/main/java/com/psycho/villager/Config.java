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

    /** Probabilidad de que el asesino suelte una lista de nombres despues de matar. */
    public static final float LIST_DROP_CHANCE = 0.35F;
    /** Si es false, el nombre del asesino NO sale en la lista (pista para encontrarlo). */
    public static final boolean LIST_INCLUDES_KILLER = false;
}
