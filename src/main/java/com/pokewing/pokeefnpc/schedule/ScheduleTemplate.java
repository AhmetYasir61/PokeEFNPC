package com.pokewing.pokeefnpc.schedule;

/**
 * A day, divided up.
 *
 * <p>Each template answers one question — "given the hour, what is this kind of
 * person doing?" — and the answer is a plain lookup rather than a state machine,
 * so an NPC that is knocked out of its routine (dragged off, shut in a room,
 * pulled into a fight) simply rejoins wherever the clock has got to instead of
 * needing to be walked back through the states it missed.
 *
 * <p>Hours are Minecraft day-time hours: 0 is 06:00 in-game (dawn, when
 * {@code level.getDayTime() % 24000 == 0}), 6 is noon, 12 is dusk, 18 is
 * midnight. {@link #hourOf(long)} does that conversion so nobody has to remember
 * the offset.
 */
public enum ScheduleTemplate {

    /** Out in the fields at first light, home before dark. */
    FARMHAND {
        @Override
        public Activity at(int hour) {
            if (hour < 1) return Activity.HOME_IDLE;
            if (hour < 5) return Activity.WORK;
            if (hour < 6) return Activity.EAT;
            if (hour < 11) return Activity.WORK;
            if (hour < 13) return Activity.SOCIALISE;
            if (hour < 14) return Activity.HOME_IDLE;
            return Activity.SLEEP;
        }
    },

    /** A workshop day: long, indoors, with a proper break in the middle. */
    ARTISAN {
        @Override
        public Activity at(int hour) {
            if (hour < 1) return Activity.HOME_IDLE;
            if (hour < 6) return Activity.WORK;
            if (hour < 7) return Activity.EAT;
            if (hour < 12) return Activity.WORK;
            if (hour < 14) return Activity.SOCIALISE;
            return Activity.SLEEP;
        }
    },

    /** Counter hours. The shop is genuinely shut outside them. */
    SHOPKEEPER {
        @Override
        public Activity at(int hour) {
            if (hour < 1) return Activity.HOME_IDLE;
            if (hour < 6) return Activity.TEND_SHOP;
            if (hour < 7) return Activity.EAT;
            if (hour < 12) return Activity.TEND_SHOP;
            if (hour < 14) return Activity.SOCIALISE;
            return Activity.SLEEP;
        }
    },

    /** Day watch: post, patrol, drill, home. */
    WATCH_DAY {
        @Override
        public Activity at(int hour) {
            if (hour < 1) return Activity.HOME_IDLE;
            if (hour < 4) return Activity.STAND_WATCH;
            if (hour < 6) return Activity.PATROL;
            if (hour < 7) return Activity.EAT;
            if (hour < 9) return Activity.TRAIN;
            if (hour < 12) return Activity.PATROL;
            if (hour < 13) return Activity.SOCIALISE;
            return Activity.SLEEP;
        }
    },

    /** Night watch: asleep through the afternoon, on the wall after dusk. */
    WATCH_NIGHT {
        @Override
        public Activity at(int hour) {
            if (hour < 2) return Activity.HOME_IDLE;
            if (hour < 9) return Activity.SLEEP;
            if (hour < 11) return Activity.FORTIFY;
            if (hour < 12) return Activity.EAT;
            if (hour < 18) return Activity.PATROL;
            return Activity.STAND_WATCH;
        }
    },

    /** Guild life: hall in the morning, contracts by day, tavern at night. */
    ADVENTURING {
        @Override
        public Activity at(int hour) {
            if (hour < 1) return Activity.HOME_IDLE;
            if (hour < 3) return Activity.SOCIALISE;
            if (hour < 10) return Activity.ROAM;
            if (hour < 12) return Activity.TRAIN;
            if (hour < 15) return Activity.SOCIALISE;
            return Activity.SLEEP;
        }
    },

    /** Shrine hours, with the evening given over to the sick. */
    CLERGY {
        @Override
        public Activity at(int hour) {
            if (hour < 1) return Activity.HOME_IDLE;
            if (hour < 5) return Activity.WORSHIP;
            if (hour < 7) return Activity.WORK;
            if (hour < 11) return Activity.WORSHIP;
            if (hour < 14) return Activity.SOCIALISE;
            return Activity.SLEEP;
        }
    },

    /** Awake when honest people are not. Alchemists, fences and thieves. */
    NOCTURNAL {
        @Override
        public Activity at(int hour) {
            if (hour < 8) return Activity.SLEEP;
            if (hour < 11) return Activity.HOME_IDLE;
            if (hour < 12) return Activity.EAT;
            if (hour < 16) return Activity.WORK;
            if (hour < 20) return Activity.SMUGGLE;
            return Activity.PROWL;
        }
    },

    /** No fixed workplace: the square, the roads, and whoever will listen. */
    VAGRANT {
        @Override
        public Activity at(int hour) {
            if (hour < 2) return Activity.SOCIALISE;
            if (hour < 6) return Activity.WORK;
            if (hour < 8) return Activity.SOCIALISE;
            if (hour < 12) return Activity.ROAM;
            if (hour < 15) return Activity.SOCIALISE;
            return Activity.SLEEP;
        }
    },

    /** Between settlements more often than in one. */
    WANDERER {
        @Override
        public Activity at(int hour) {
            if (hour < 1) return Activity.HOME_IDLE;
            if (hour < 5) return Activity.ROAM;
            if (hour < 6) return Activity.TEND_SHOP;
            if (hour < 11) return Activity.ROAM;
            if (hour < 14) return Activity.SOCIALISE;
            return Activity.SLEEP;
        }
    },

    /** A late start, a short day, and a long evening. */
    NOBLE_LEISURE {
        @Override
        public Activity at(int hour) {
            if (hour < 3) return Activity.HOME_IDLE;
            if (hour < 6) return Activity.WORK;
            if (hour < 8) return Activity.EAT;
            if (hour < 12) return Activity.SOCIALISE;
            if (hour < 16) return Activity.HOME_IDLE;
            return Activity.SLEEP;
        }
    };

    /** What this kind of person is doing in the given day-time hour, 0..23. */
    public abstract Activity at(int hour);

    /** Converts a level's raw day time into the 0..23 hour this enum expects. */
    public static int hourOf(long dayTime) {
        return (int) (Math.floorMod(dayTime, 24000L) / 1000L);
    }

    public Activity forDayTime(long dayTime) {
        return at(hourOf(dayTime));
    }

    /**
     * How far into the current hour we are, in [0,1). Used to stagger a crowd:
     * without it every NPC in the village changes activity on the same tick and
     * the whole square turns at once.
     */
    public static float hourProgress(long dayTime) {
        return Math.floorMod(dayTime, 1000L) / 1000.0F;
    }
}
