package trplugins.menu.module.display;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import trplugins.menu.api.event.MenuOpenEvent.Reason;
import trplugins.menu.api.receptacle.ReceptacleAPIKt;

/**
 * Callback/conditions/receptacle ordering copied from real Menu.open; no Bukkit open in packet mode.
 */
public final class Menu {

    public boolean cancelled, condition = true;

    public Runnable bukkitOpen = () -> {
    };

    public Consumer<MenuSession> conditionHook = s -> {
    };

    public interface Function2 {

        Object invoke(Object p, Object r);
    }

    public static final class Window {

        public Player viewer;

        public Function2 nativeClose;

        private Function2 onClose = (p, r) -> null;

        public Function2 getOnClose() {
            return onClose;
        }

        public void setOnClose(Function2 f) {
            onClose = f;
        }

        public void close(Player p) {
            // 真实 close(false) 在回调返回后无条件清 viewing，最后才清 viewer。
            // 参数只为兼容既有测试；不能用调用者传入的玩家代替对象持有的 viewer。
            if (viewer != null) {
                onClose.invoke(viewer, this);
                ReceptacleAPIKt.viewing.remove(viewer.getUniqueId());
                viewer = null;
            }
        }
    }

    public void open(Player p, Integer page, Reason reason, Consumer<MenuSession> block) {
        MenuSession s = MenuSession.SESSIONS.computeIfAbsent(p.getUniqueId(), k -> new MenuSession(p));
        // real same-menu page path never calls the Consumer
        if (s.menu == this) {
            return;
        }
        s.menu = null;
        s.receptacle = null;
        if (cancelled) {
            return;
        }
        s.menu = this;
        block.accept(s);
        conditionHook.accept(s);
        if (!condition) {
            s.menu = null;
            return;
        }
        if (s.menu != this) {
            return;
        }
        Window w = new Window();
        s.receptacle = w;
        w.viewer = p;
        w.nativeClose = (player, r) -> {
            s.menu = null;
            s.receptacle = null;
            return null;
        };
        w.setOnClose(w.nativeClose);
        bukkitOpen.run();
        ReceptacleAPIKt.viewing.put(p.getUniqueId(), w);
    }
}
