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

        private volatile boolean closed;

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
                return !closed && sessions.get(viewer.getUniqueId()) == session && read(session, "getViewer") == viewer && id.equals(read(session, "getId")) && read(session, "getMenu") == menu;
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

        boolean opened(Runnable close) throws ReflectiveOperationException {
            receptacle = read(session, "getReceptacle");
            if (!current()) {
                return false;
            }
            // Natural packet closes do not emit Bukkit InventoryCloseEvent (or MenuCloseEvent).
            // Session.close / MenuCloseEvent also reach this callback. Preserve TrMenu's own cleanup.
            Object original = read(receptacle, "getOnClose");
            Method setter = java.util.Arrays.stream(receptacle.getClass().getMethods()).filter(m -> m.getName().equals("setOnClose") && m.getParameterCount() == 1).findFirst().orElseThrow();
            Class<?> function = setter.getParameterTypes()[0];
            Method invoke = java.util.Arrays.stream(function.getMethods()).filter(m -> m.getName().equals("invoke") && m.getParameterCount() == 2).findFirst().orElseThrow();
            setter.invoke(receptacle, Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[] { function }, (p, m, a) -> {
                if (m.getName().equals("invoke")) {
                    closed = true;
                    // removes only the captured flow; an old window cannot cancel a new selection
                    close.run();
                    // delayed old close must not shut a reused session
                    if (read(session, "getReceptacle") != receptacle) {
                        return null;
                    }
                    try {
                        return invoke.invoke(original, a);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
                if (m.getName().equals("toString")) {
                    return "PetResetConfirm close";
                }
                if (m.getName().equals("hashCode")) {
                    return System.identityHashCode(p);
                }
                return p == a[0];
            }));
            return current();
        }
    }

    private static Object read(Object object, String method) throws ReflectiveOperationException {
        return object.getClass().getMethod(method).invoke(object);
    }

    static boolean open(JavaPlugin plugin, Player player, Consumer<Window> bind, Runnable close) throws ReflectiveOperationException {
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
        return menu != null && open(menu, player, bind, close, prefix);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static boolean open(Object menu, Player player, Consumer<Window> bind, Runnable close, String prefix) throws ReflectiveOperationException {
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
        return opened[0] != null && opened[0].opened(close);
    }
}
