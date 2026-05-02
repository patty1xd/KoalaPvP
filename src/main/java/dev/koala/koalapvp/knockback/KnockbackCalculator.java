package dev.koala.koalapvp.knockback;

import dev.koala.koalapvp.config.KoalaConfig;
import dev.koala.koalapvp.util.Logger;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class KnockbackCalculator {

    private final KoalaConfig cfg;

    public KnockbackCalculator(KoalaConfig cfg) { this.cfg = cfg; }

    public KnockbackProfile compute(Player attacker, Player victim) {

        // 1. Cooldown charge gate
        float charge = attacker.getAttackCooldown();
        if (cfg.isCooldownEnabled() && charge < cfg.getMinChargeThreshold()) {
            if (cfg.isLogHits()) Logger.debug("Hit rejected — charge too low: " + charge);
            return KnockbackProfile.rejected();
        }

        // 2. Base values (ground vs air)
        boolean onGround = victim.isOnGround();
        double baseH = onGround ? cfg.getGroundHorizontal() : cfg.getAirHorizontal();
        double baseV = onGround ? cfg.getGroundVertical()   : cfg.getAirVertical();

        // 3. Sprint bonus
        if (attacker.isSprinting()) baseH += cfg.getSprintBonus();

        // 4. Enchantment bonus
        ItemStack held = attacker.getInventory().getItemInMainHand();
        int kbLevel = 0;
        if (held != null) kbLevel = held.getEnchantmentLevel(Enchantment.KNOCKBACK);
        if (kbLevel == 1) {
            baseH += cfg.getKb1HorizontalAdd();
            baseV += cfg.getKb1VerticalAdd();
        } else if (kbLevel >= 2) {
            baseH += cfg.getKb2HorizontalAdd();
            baseV += cfg.getKb2VerticalAdd();
        }

        // 5. Netherite resistance
        if (cfg.isRespectNetheriteResistance()) {
            double resistance = getNetheriteResistance(victim);
            baseH *= (1.0 - resistance);
            baseV *= (1.0 - resistance);
        }

        // 6. Cooldown scaling
        if (cfg.isCooldownEnabled() && cfg.isScaleKnockback()) {
            baseH *= charge;
            baseV *= charge;
        }

        // 7. Direction — attacker → victim, horizontal only, normalised
        Vector dir = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        dir.setY(0);
        double length = dir.length();
        if (length < 0.001) {
            dir = attacker.getLocation().getDirection();
            dir.setY(0);
            length = dir.length();
        }
        if (length < 0.001) { dir.setX(1); length = 1; }
        dir.multiply(1.0 / length);

        // 8. Apply friction on ground
        double horizFinal = onGround ? baseH / cfg.getGroundFriction() : baseH;

        Vector finalVec = dir.multiply(horizFinal);
        finalVec.setY(baseV);

        // 9. Clamp vertical
        if (finalVec.getY() > cfg.getMaxVerticalVelocity())
            finalVec.setY(cfg.getMaxVerticalVelocity());

        if (cfg.isLogHits()) {
            Logger.debug(String.format(
                "KB | %s→%s charge=%.2f kb=%d ground=%b vec=(%.3f,%.3f,%.3f)",
                attacker.getName(), victim.getName(), charge, kbLevel, onGround,
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
