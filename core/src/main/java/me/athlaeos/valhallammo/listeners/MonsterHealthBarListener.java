package me.athlaeos.valhallammo.listeners;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.utility.BossBarUtils;
import me.athlaeos.valhallammo.utility.Utils;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Locale;

/**
 * Shows the attacked monster's name and health in a boss bar (top-center) to the attacking player.
 * The bar stays up for a configurable amount of time and the timer resets every time the player
 * hits the monster again.
 */
public class MonsterHealthBarListener implements Listener {
    // identifier under which the player's monster-health boss bar is tracked (one reused bar per player)
    private static final String KEY = "monster_health";

    private final boolean enabled = ValhallaMMO.getPluginConfig().getBoolean("monster_healthbar_enabled", true);
    // duration is given in seconds in the config, converted to the tenth-seconds BossBarUtils expects
    private final int duration = Math.max(1, ValhallaMMO.getPluginConfig().getInt("monster_healthbar_duration", 5)) * 10;
    private final boolean showPlayers = ValhallaMMO.getPluginConfig().getBoolean("monster_healthbar_show_players", false);
    private final String titleFormat = ValhallaMMO.getPluginConfig().getString("monster_healthbar_title", "&c%monster% &7%hp%&8/&7%max_hp% &c❤");

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e){
        if (!enabled) return;
        if (ValhallaMMO.isWorldBlacklisted(e.getEntity().getWorld().getName())) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (!showPlayers && victim instanceof Player) return;

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        // health only updates after this event resolves, so read it on the next tick
        ValhallaMMO.getInstance().getServer().getScheduler().runTask(ValhallaMMO.getInstance(), () -> {
            if (!attacker.isOnline() || !victim.isValid()) return;
            showHealthBar(attacker, victim);
        });
    }

    private void showHealthBar(Player attacker, LivingEntity victim){
        double max = victim.getMaxHealth();
        double current = Math.max(0, Math.min(victim.getHealth(), max));
        double fraction = max <= 0 ? 0 : current / max;
        if (current <= 0) return; // monster died, nothing to show

        String title = Utils.chat(titleFormat
                .replace("%monster%", monsterName(victim))
                .replace("%hp%", format(current))
                .replace("%max_hp%", format(max)));
        BossBarUtils.showBossBarToPlayer(attacker, title, fraction, duration, KEY, BarColor.RED, BarStyle.SOLID);
    }

    private Player resolveAttacker(Entity damager){
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private String monsterName(LivingEntity victim){
        if (victim.getCustomName() != null && !victim.getCustomName().isBlank()) return victim.getCustomName();
        String raw = victim.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")){
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private String format(double health){
        // show whole numbers without decimals, fractional health with one decimal
        return health == Math.floor(health) ? String.valueOf((long) health) : String.format("%.1f", health);
    }
}
