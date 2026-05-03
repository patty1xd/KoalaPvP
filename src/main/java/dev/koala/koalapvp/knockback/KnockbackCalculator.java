package dev.koala.koalapvp.knockback;

import dev.koala.koalapvp.config.KoalaConfig;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Pure deterministic knockback math.
 * No scheduling, no side-effects — just in → KnockbackProfile.
 *
 * IMPORTANT: charge must be captured at event-fire time (before the 0-tick
 * delay) and passed in here. Reading getAttackCooldown() inside a delayed
 * task returns 1.0 (already reset) and makes cooldown scaling useless.
 */
public final class KnockbackCalculator {

    private final KoalaConfig cfg;

    public KnockbackCalculator(KoalaConfig cfg) {
        this.cfg = cfg;
    }

    /**
     * @param attacker the player dealing damage
     * @param victim   the player receiving damage
     * @param charge   attack cooldown fraction [0.0–1.0] captured at event time
     */
    public KnockbackProfile compute(Player attacker, Player victim, float charge) {

        // ── 1. Cooldown gate ─────────────────────────────────────────────
        if (cfg.isCooldownEnabled() && charge < cfg.getMinChargeThreshold()) {
            if (cfg.isLogHits())
                Logger.debug("Hit rejected — charge too low: " + charge);
            return KnockbackProfile.rejected();
        }

        // ── 2. Base values (ground vs air) ────────────────────────────────
        boolean onGround = victim.isOnGround();
        double baseH = onGround ? cfg.getGroundHorizontal() : cfg.getAirHorizontal();
        double baseV = onGround ? cfg.getGroundVertical()   : cfg.getAirVertical();

        // ── 3. Sprint bonus ───────────────────────────────────────────────
        if (attacker.isSprinting()) baseH += cfg.getSprintBonus();

        // ── 4. Enchantment bonus ──────────────────────────────────────────
        ItemStack held = attacker.getInventory().getItemInMainHand();
        int kbLevel = (held != null) ? held.getEnchantmentLevel(Enchantment.KNOCKBACK) : 0;
        if (kbLevel == 1) {
            baseH += cfg.getKb1HorizontalAdd();
            baseV += cfg.getKb1VerticalAdd();
        } else if (kbLevel >= 2) {
            baseH += cfg.getKb2HorizontalAdd();
            baseV += cfg.getKb2VerticalAdd();
        }

        // ── 5. Netherite resistance ───────────────────────────────────────
        if (cfg.isRespectNetheriteResistance()) {
            double r = getNetheriteResistance(victim);
            baseH *= (1.0 - r);
            baseV *= (1.0 - r);
        }

        // ── 6. Cooldown scaling — applied AFTER all additive bonuses ──────
        // charge is the value captured at event time, not re-read from player.
        if (cfg.isCooldownEnabled() && cfg.isScaleKnockback()) {
            // Use squared charge so partial hits feel noticeably weaker,
            // matching how vanilla damage scales (charge^2).
            double scale = charge * charge;
            baseH *= scale;
            baseV *= scale;
        }

        // ── 7. Direction — horizontal only, normalised ────────────────────
        Vector dir = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        dir.setY(0);
        double len = dir.length();
        if (len < 0.001) {
            dir = attacker.getLocation().getDirection();
            dir.setY(0);
            len = dir.length();
        }
        if (len < 0.001) { dir.setX(1); len = 1; }
        dir.multiply(1.0 / len);

        // ── 8. Apply friction on ground ───────────────────────────────────
        double horizFinal = onGround ? baseH / cfg.getGroundFriction() : baseH;

        Vector finalVec = dir.multiply(horizFinal);
        finalVec.setY(baseV);

        // ── 9. Clamp vertical ─────────────────────────────────────────────
        if (finalVec.getY() > cfg.getMaxVerticalVelocity())
            finalVec.setY(cfg.getMaxVerticalVelocity());

        if (cfg.isLogHits()) {
            Logger.debug(String.format(
                "KB | %s→%s charge=%.2f(scale=%.2f) kb=%d ground=%b vec=(%.3f,%.3f,%.3f)",
                attacker.getName(), victim.getName(),
                charge, charge * charge,
                kbLevel, onGround,
                finalVec.getX(), finalVec.getY(), finalVec.getZ()));
        }

        return new KnockbackProfile(finalVec, cfg.getSmoothingTicks());
    }

    private double getNetheriteResistance(Player victim) {
        int n = 0;
        for (ItemStack piece : victim.getInventory().getArmorContents()) {
            if (piece != null && piece.getType().name().startsWith("NETHERITE_")) n++;
        }
        return Math.min(n * 0.10, 0.40);
    }
}
