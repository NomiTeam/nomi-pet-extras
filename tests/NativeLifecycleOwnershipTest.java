package team.nomi.pet;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import trplugins.menu.module.display.Menu;
import trplugins.menu.module.display.MenuSession;

/** 同一断言可运行于移除钩子前后，验证原生回调所有权。 */
public final class NativeLifecycleOwnershipTest {

    public static void main(String[] args) throws Exception {
        UUID id = UUID.randomUUID();
        Player player = BridgeSafetyTest.proxy(Player.class, (p, m, a) -> {
            if (m.getName().equals("getUniqueId")) {
                return id;
            }
            throw new AssertionError(m.getName());
        });
        // 兼容旧内部签名只用于对同一契约执行 RED→GREEN；不增加生产兼容入口。
        Method open = java.util.Arrays.stream(TrMenuConfirm.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("open") && m.getParameterTypes()[0] == Object.class)
                .findFirst().orElseThrow();
        Consumer<TrMenuConfirm.Window> bind = w -> { };
        Object result = open.getParameterCount() == 5
                ? open.invoke(null, new Menu(), player, bind, (Runnable) () -> { }, "trplugins.menu")
                : open.invoke(null, new Menu(), player, bind, "trplugins.menu");
        TrMenuSessionTest.check(Boolean.TRUE.equals(result), "native open works");
        Menu.Window window = MenuSession.SESSIONS.get(id).receptacle;
        TrMenuSessionTest.check(window.getOnClose() == window.nativeClose,
                "native close callback must remain unmodified");
        window.close(player);
        TrMenuSessionTest.check(window.viewer == null && MenuSession.SESSIONS.get(id).receptacle == null,
                "complete native close finishes normally");
        System.out.println("Exact native onClose ownership and complete normal close passed");
    }
}
