package trplugins.menu.api.receptacle;

import java.util.*;
import org.bukkit.entity.Player;

public final class ReceptacleAPIKt {

    public static final Map<UUID, Object> viewing = new HashMap<>();

    public static Object getViewingReceptacle(Player p) {
        return viewing.get(p.getUniqueId());
    }
}
