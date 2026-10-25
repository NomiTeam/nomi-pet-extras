package team.nomi.pet;

import java.util.*;
import org.bukkit.entity.Player;
import trplugins.menu.module.display.*;
import trplugins.menu.api.receptacle.ReceptacleAPIKt;

public class TrMenuSessionTest {

    static void check(boolean b, String s) {
        if (!b) {
            throw new AssertionError(s);
        }
    }

    public static void main(String[] args) throws Exception {
        for (boolean bukkit : new boolean[] { false, true }) {
            UUID id = UUID.randomUUID();
            Player p = BridgeSafetyTest.proxy(Player.class, (o, m, a) -> {
                if (m.getName().equals("getUniqueId")) {
                    return id;
                }
                throw new AssertionError(m.getName());
            });
            Object c = new Object();
            var state = SelectionTest.selected(c);
            state.handoff(new Object(), org.bukkit.event.inventory.InventoryType.CRAFTING);
            Menu menu = new Menu();
            int[] opens = { 0 }, closes = { 0 };
            TrMenuConfirm.Window[] w = { null };
            menu.bukkitOpen = () -> {
                if (bukkit) {
                    opens[0]++;
                    check(state.open(new Object()), "static open accepted");
                }
            };
            check(TrMenuConfirm.open(menu, p, x -> {
                w[0] = x;
                check(x.rendering(), "callback session bound");
                check(!x.current(), "callback precedes window");
                check(!state.prepare(), "render cannot prepare");
            }, () -> closes[0]++, "trplugins.menu"), "open");
            check(opens[0] == (bukkit ? 1 : 0), "packet has no InventoryOpenEvent");
            check(w[0].current() && state.confirm(w[0]) && state.visible(w[0]) && state.prepare(), "prepare live window");
            MenuSession session = MenuSession.SESSIONS.get(id);
            Menu.Window old = session.receptacle;
            session.menu = new Menu();
            check(!w[0].current(), "changed menu");
            session.menu = menu;
            session.receptacle = new Menu.Window();
            check(!w[0].current(), "changed window");
            session.receptacle = old;
            ReceptacleAPIKt.viewing.remove(id);
            check(!w[0].current(), "packet window no longer viewing");
            ReceptacleAPIKt.viewing.put(id, old);
            MenuSession replacement = new MenuSession(p);
            replacement.menu = menu;
            replacement.receptacle = old;
            MenuSession.SESSIONS.put(id, replacement);
            check(!w[0].current(), "new session same menu/window");
            MenuSession.SESSIONS.put(id, session);
            menu.bukkitOpen = () -> {
            };
            old.close(p);
            check(!w[0].current() && closes[0] == 1 && session.menu == null, "close callback plus native cleanup");
            check(TrMenuConfirm.open(menu, p, x -> w[0] = x, () -> closes[0] += 10, "trplugins.menu"), "new flow");
            old.getOnClose().invoke(p, old);
            check(closes[0] == 2, "old callback only old captured request");
            check(w[0].current(), "stale close preserves new session and request");
            MenuSession.SESSIONS.get(id).receptacle.close(p);
            menu.cancelled = true;
            check(!TrMenuConfirm.open(menu, p, x -> {
                throw new AssertionError("cancel callback");
            }, () -> {
            }, "trplugins.menu"), "open cancellation");
            menu.cancelled = false;
            menu.condition = false;
            check(!TrMenuConfirm.open(menu, p, x -> {
            }, () -> {
            }, "trplugins.menu"), "open condition rejects after callback");
            menu.condition = true;
            menu.conditionHook = s -> {
                s.menu = new Menu();
            };
            check(!TrMenuConfirm.open(menu, p, x -> {
            }, () -> {
            }, "trplugins.menu"), "condition switches menu");
            menu.conditionHook = s -> {
            };
            check(TrMenuConfirm.open(menu, p, x -> {
            }, () -> {
            }, "trplugins.menu"), "next session");
            check(!TrMenuConfirm.open(menu, p, x -> {
                throw new AssertionError("same menu callback");
            }, () -> {
            }, "trplugins.menu"), "same-menu page must not reuse old request");
        }
        System.out.println("TrMenu 3.12.5 session model: packet/static callback ordering, cancellation, conditions, window/session identity and stale closes passed");
    }
}
