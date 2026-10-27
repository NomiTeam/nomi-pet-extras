package team.nomi.pet;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class NomiPetExtras extends JavaPlugin {

    private PetMenus menus;

    @Override
    public void onEnable() {
        try {
            menus = new PetMenus(this);
            getServer().getPluginManager().registerEvents(menus, this);
            getCommand("nomipetreset").setExecutor(menus);
        } catch (LinkageError | RuntimeException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Required MyPet GUI/API unavailable", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.shutdown();
        }
    }

    public boolean prepare(Player p) {
        return menus != null && menus.prepare(p);
    }

    public String apply(Player p) {
        return menus == null ? "REJECTED" : menus.apply(p);
    }

    public void cancel(Player p) {
        if (menus != null) {
            menus.cancel(p);
        }
    }

    public String getTargetName(Player p) {
        return menus == null ? "" : menus.getTargetName(p);
    }
}
