package me.athlaeos.valhallammo.utility;

import me.athlaeos.valhallammo.ValhallaMMO;
import me.athlaeos.valhallammo.dom.MinecraftVersion;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * Renders the skill experience / level-up feedback in a temporary scoreboard sidebar
 * (the side panel of the screen) instead of a boss bar (top-center). This frees the
 * boss bar up for the monster health display.
 * <p>
 * The sidebar is shown for a limited amount of time and the timer resets every time the
 * player gains experience again, mirroring how the old boss bar behaved.
 */
public class LevelSidebarUtils {
    private static final Map<UUID, LevelSidebar> activeSidebars = new HashMap<>();
    private static final ScoreboardManager manager = ValhallaMMO.getInstance().getServer().getScoreboardManager();
    // each line needs a unique, (visually) invisible entry string; colour codes work nicely for that
    private static final List<String> entryKeys = List.of(
            "&0&r", "&1&r", "&2&r", "&3&r", "&4&r", "&5&r", "&6&r", "&7&r", "&8&r", "&9&r"
    );

    /**
     * Shows (or refreshes) the level-up sidebar for a player.
     * @param player the player to show the sidebar to
     * @param title  the sidebar title (typically the skill name)
     * @param lines  the content lines, top to bottom
     * @param time   how long to keep it on screen, in TENTH SECONDS
     */
    public static void showLevelSidebar(final Player player, String title, List<String> lines, int time){
        if (manager == null) return;
        LevelSidebar sidebar = activeSidebars.get(player.getUniqueId());
        if (sidebar == null){
            sidebar = new LevelSidebar(player, title);
            sidebar.runTaskTimer(ValhallaMMO.getInstance(), 0L, 2L);
            activeSidebars.put(player.getUniqueId(), sidebar);
        }
        sidebar.update(title, lines, time);
    }

    public static void hideLevelSidebar(final Player player){
        LevelSidebar sidebar = activeSidebars.remove(player.getUniqueId());
        if (sidebar != null) sidebar.remove();
    }

    /**
     * Builds a textual progress bar (the sidebar cannot render a real bar like a boss bar can).
     * @param fraction progress between 0 and 1
     */
    public static String progressBar(double fraction){
        fraction = Math.max(0, Math.min(1, fraction));
        int total = 10;
        int filled = (int) Math.round(fraction * total);
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < total; i++){
            if (i == filled) sb.append("&7");
            sb.append('|');
        }
        sb.append(" &f").append(String.format("%.0f%%", fraction * 100));
        return sb.toString();
    }

    private static class LevelSidebar extends BukkitRunnable {
        private final Player p;
        private final Scoreboard previous;
        private final Scoreboard scoreboard;
        private final Objective objective;
        private int timer = 0;
        private List<String> lastLines = Collections.emptyList();

        private LevelSidebar(Player p, String title){
            this.p = p;
            this.previous = p.getScoreboard();
            this.scoreboard = manager.getNewScoreboard();
            if (MinecraftVersion.currentVersionNewerThan(MinecraftVersion.MINECRAFT_1_20)) {
                this.objective = scoreboard.registerNewObjective("valhalla_level", Criteria.DUMMY, Utils.chat(title));
            } else {
                this.objective = scoreboard.registerNewObjective("valhalla_level", "dummy", Utils.chat(title));
            }
            this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        private void update(String title, List<String> lines, int time){
            this.timer = time;
            objective.setDisplayName(Utils.chat(title));
            if (!lines.equals(lastLines)){
                render(lines);
                lastLines = new ArrayList<>(lines);
            }
            if (!scoreboard.equals(p.getScoreboard())) p.setScoreboard(scoreboard);
        }

        private void render(List<String> lines){
            scoreboard.getTeams().forEach(Team::unregister);
            int count = Math.min(lines.size(), entryKeys.size());
            for (int i = 0; i < count; i++){
                String entry = Utils.chat(entryKeys.get(i));
                Team team = scoreboard.registerNewTeam("l" + i);
                team.addEntry(entry);
                team.setPrefix(Utils.chat(lines.get(i)));
                // higher score = higher up the board, so the first line should hold the biggest score
                objective.getScore(entry).setScore(count - i);
            }
        }

        private void remove(){
            try {
                if (p.getScoreboard().equals(scoreboard))
                    p.setScoreboard(previous != null ? previous : manager.getMainScoreboard());
            } catch (IllegalStateException ignored){}
            try { cancel(); } catch (IllegalStateException ignored){}
        }

        @Override
        public void run(){
            if (!p.isOnline() || timer <= 0){
                activeSidebars.remove(p.getUniqueId());
                remove();
                return;
            }
            if (!scoreboard.equals(p.getScoreboard())) p.setScoreboard(scoreboard);
            timer--;
        }
    }
}
