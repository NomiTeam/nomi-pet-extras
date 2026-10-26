package team.nomi.pet;

import java.lang.reflect.*;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Fixed PetResetConfirm adapter. TrMenu sessions are reused; the receptacle identifies this opening.
 */
final class TrMenuConfirm {

    static final class Window {

        private final Object session, menu, id;

        private final Player viewer;

        private final Map<?, ?> sessions;

        private final Method viewing;

        private Object receptacle;

        Window(Object session, Object menu, Player viewer, ClassLoader loader, String prefix) throws ReflectiveOperationException {
            this.session = session;
            this.menu = menu;
            this.viewer = viewer;
            id = read(session, "getId");
            sessions = (Map<?, ?>) session.getClass().getField("SESSIONS").get(null);
            viewing = Class.forName(prefix + ".api.receptacle.ReceptacleAPIKt", true, loader).getMethod("getViewingReceptacle", Player.class);
        }

        boolean rendering() {
            try {
                return sessions.get(viewer.getUniqueId()) == session && read(session, "getViewer") == viewer && id.equals(read(session, "getId")) && read(session, "getMenu") == menu;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                return false;
            }
        }

        boolean current() {
            try {
                return rendering() && receptacle != null && read(session, "getReceptacle") == receptacle && viewing.invoke(null, viewer) == receptacle;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                return false;
            }
        }

        boolean opened() throws ReflectiveOperationException {
            receptacle = read(session, "getReceptacle");
            // 只读取原生会话身份；不替换 onClose，关闭及其尾部清理由 TrMenu 完成。
            return current();
        }
    }

    private static Object read(Object object, String method) throws ReflectiveOperationException {
        return object.getClass().getMethod(method).invoke(object);
    }

    static boolean open(JavaPlugin plugin, Player player, Consumer<Window> bind) throws ReflectiveOperationException {
        var trmenu = plugin.getServer().getPluginManager().getPlugin("TrMenu");
        if (trmenu == null || !trmenu.isEnabled()) {
            return false;
        }
        ClassLoader loader = trmenu.getClass().getClassLoader();
        String prefix = "me.arasple.mc.trmenu";
        Class<?> api;
        try {
            api = Class.forName(prefix + ".api.TrMenuAPI", true, loader);
        } catch (ClassNotFoundException absent) {
            prefix = "trplugins.menu";
            api = Class.forName(prefix + ".api.TrMenuAPI", true, loader);
        }
        Object menu = api.getMethod("getMenuById", String.class).invoke(api.getField("INSTANCE").get(null), "PetResetConfirm");
        return menu != null && open(menu, player, bind, prefix);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static boolean open(Object menu, Player player, Consumer<Window> bind, String prefix) throws ReflectiveOperationException {
        ClassLoader loader = menu.getClass().getClassLoader();
        Class<? extends Enum> reason = (Class<? extends Enum>) Class.forName(prefix + ".api.event.MenuOpenEvent$Reason", true, loader);
        Window[] opened = { null };
        menu.getClass().getMethod("open", Player.class, Integer.class, reason, Consumer.class).invoke(menu, player, null, Enum.valueOf(reason, "BINDING_COMMANDS"), (Consumer<Object>) session -> {
            try {
                Window window = new Window(session, menu, player, loader, prefix);
                if (window.rendering()) {
                    opened[0] = window;
                    bind.accept(window);
                }
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        });
        // The Consumer runs before open conditions/rendering. A cancelled/reentrant opening must fail closed.
        return opened[0] != null && opened[0].opened();
    }
}
