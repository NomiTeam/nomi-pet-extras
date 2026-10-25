package trplugins.menu.module.display;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Offline model of the supplied 3.12.5 javap API; sessions survive window changes.
 */
public final class MenuSession {

    public static final Map<UUID, MenuSession> SESSIONS = new ConcurrentHashMap<>();

    public final Player viewer;

    public final int id;

    public Menu menu;

    public Menu.Window receptacle;

    public MenuSession(Player p) {
        viewer = p;
        id = SESSIONS.size();
    }

    public int getId() {
        return id;
    }

    public Player getViewer() {
        return viewer;
    }

    public Menu getMenu() {
        return menu;
    }

    public Menu.Window getReceptacle() {
        return receptacle;
    }
}
