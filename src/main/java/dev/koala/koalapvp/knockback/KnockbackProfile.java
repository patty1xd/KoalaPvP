package dev.koala.koalapvp.knockback;

import org.bukkit.util.Vector;

/**
 * Immutable result of one knockback calculation.
 * Carries the final velocity vector to apply, or marks the hit as rejected.
 */
public final class KnockbackProfile {

    private final Vector velocity;
    private final boolean valid;

    public KnockbackProfile(Vector velocity) {
        this.velocity = velocity.clone();
        this.valid    = true;
    }

    private KnockbackProfile() {
        this.velocity = new Vector(0, 0, 0);
        this.valid    = false;
    }

    public static KnockbackProfile rejected() {
        return new KnockbackProfile();
    }

    public Vector  getVelocity() { return velocity.clone(); }
    public boolean isValid()     { return valid; }
}
