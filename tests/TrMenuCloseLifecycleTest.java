package team.nomi.pet;

import java.util.UUID;
import org.bukkit.entity.Player;
import trplugins.menu.api.receptacle.ReceptacleAPIKt;
import trplugins.menu.module.display.Menu;
import trplugins.menu.module.display.MenuSession;

/** 完整原生 close 的确定性交错；不把回调结束当作尾部清理完成。 */
public final class TrMenuCloseLifecycleTest {

    public static void main(String[] args) throws Exception {
        for (boolean reentrant : new boolean[] { false, true }) {
            run(reentrant);
        }
        System.out.println("Native callback retained; complete captured/reentrant close invalidates stale window; post-close open works");
    }

    private static void run(boolean reentrant) throws Exception {
        UUID id = UUID.randomUUID();
        Player player = BridgeSafetyTest.proxy(Player.class, (p, m, a) -> {
            if (m.getName().equals("getUniqueId")) {
                return id;
            }
            throw new AssertionError(m.getName());
        });
        TrMenuConfirm.Window[] current = { null };
        Runnable openNext = () -> {
            try {
                TrMenuSessionTest.check(TrMenuConfirm.open(new Menu(), player,
                        w -> current[0] = w, "trplugins.menu"), "replacement opens");
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        };
        TrMenuSessionTest.check(TrMenuConfirm.open(new Menu(), player, w -> { },
                "trplugins.menu"), "old opens");
        Menu.Window captured = MenuSession.SESSIONS.get(id).receptacle;
        TrMenuSessionTest.check(captured.getOnClose() == captured.nativeClose,
                "adapter must preserve exact native callback");
        if (reentrant) {
            // 测试注入外部重入：原生回调之后、无条件尾部之前打开新窗口。
            captured.setOnClose((p, r) -> {
                captured.nativeClose.invoke(p, r);
                openNext.run();
                return null;
            });
        } else {
            openNext.run();
        }
        captured.close(player);
        TrMenuSessionTest.check(captured.viewer == null, "full close clears old viewer");
        TrMenuSessionTest.check(ReceptacleAPIKt.getViewingReceptacle(player) == null,
                "faithful native tail clears viewing even after replacement opens");
        TrMenuSessionTest.check(!current[0].current(),
                "complete old close invalidates replacement; cannot authorize business");
        // 明确在 close 返回之后继续；不使用固定延时，也不自动重开窗口。
        openNext.run();
        TrMenuSessionTest.check(current[0].current(), "fresh opening after full close works");
        Menu.Window fresh = MenuSession.SESSIONS.get(id).receptacle;
        captured.close(player);
        TrMenuSessionTest.check(current[0].current(), "fully retired old close is a native no-op");
        TrMenuSessionTest.check(fresh.getOnClose() == fresh.nativeClose,
                "fresh opening also retains native lifecycle");
        fresh.close(player);
        TrMenuSessionTest.check(!current[0].current(), "normal native close invalidates opening");
    }
}
