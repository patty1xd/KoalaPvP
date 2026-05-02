package dev.koala.koalapvp.knockback;

import org.bukkit.util.Vector;

public final class KnockbackProfile {

    private final Vector velocity;
    private final int smoothingTicks;
    private final boolean valid;

    public KnockbackProfile(Vector velocity, int smoothingTicks) {
        this.velocity       = velocity.clone();
        this.smoothingTicks = smoothingTicks;
        this.valid          = true;
    }

    private KnockbackProfile() {
        this.velocity       = new Vector(0, 0, 0);
        this.smoothingTicks = 0;
        this.valid          = false;
    }

    public static KnockbackProfile rejected() { return new KnockbackProfile(); }

    public Vector getVelocity()    { return velocity.clone(); }
    public int getSmoothingTicks() { return smoothingTicks; }
    public boolean isValid()       { return valid; }
}
