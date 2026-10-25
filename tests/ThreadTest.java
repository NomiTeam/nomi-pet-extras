package team.nomi.pet;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Allocates only the plugin shell, without a server/plugin loader; exercises real Paper thread guard.
 */
public class ThreadTest {

    static void check(boolean b, String message) {
        if (!b) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        var plugin = (NomiPetExtras) unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, NomiPetExtras.class);
        int[] guards = { 0 };
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[] { Server.class }, (p, m, a) -> {
            if (m.getName().equals("isOwnedByCurrentRegion")) {
                guards[0]++;
                return false;
            }
            throw new AssertionError("unexpected server call " + m.getName());
        });
        Field serverField = JavaPlugin.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(plugin, server);
        PetMenus menus = new PetMenus(plugin);
        Field menusField = NomiPetExtras.class.getDeclaredField("menus");
        menusField.setAccessible(true);
        menusField.set(plugin, menus);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] { Player.class }, (p, m, a) -> {
            throw new AssertionError("wrong thread accessed player: " + m.getName());
        });
        Field flowsField = PetMenus.class.getDeclaredField("flows");
        flowsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> flows = (Map<UUID, Object>) flowsField.get(menus);
        UUID key = UUID.randomUUID();
        Object sentinel = new Object();
        flows.put(key, sentinel);
        check(!plugin.prepare(player), "wrong thread prepared");
        check(plugin.apply(player).equals("REJECTED"), "wrong thread applied");
        plugin.cancel(player);
        check(plugin.getTargetName(player).isEmpty(), "wrong thread name");
        check(guards[0] == 4 && flows.get(key) == sentinel, "wrong thread mutated state");
        System.out.println("Real Paper entity ownership guard: prepare/apply/cancel/name reject without player/state access passed");
    }
}
