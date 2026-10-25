package team.nomi.pet;

import org.bukkit.event.inventory.InventoryType;

public class SelectionTest {

    static void check(boolean b, String reason) {
        if (!b) {
            throw new AssertionError(reason);
        }
    }

    static ResetSelection selected(Object chooser) {
        var state = new ResetSelection();
        state.bind(chooser);
        check(state.select(chooser), "native selection");
        check(!state.select(chooser), "duplicate callback");
        check(state.close(chooser), "legal native close");
        return state;
    }

    public static void main(String[] args) {
        Object packetChooser = new Object(), packetSession = new Object();
        var packet = selected(packetChooser);
        check(packet.handoff(new Object(), InventoryType.CRAFTING), "packet handoff");
        check(packet.confirm(packetSession), "packet open callback without InventoryOpenEvent");
        check(packet.visible(packetSession) && packet.prepare(), "packet preparation");
        for (var type : new InventoryType[] { InventoryType.CRAFTING, InventoryType.CREATIVE }) {
            Object chooser = new Object(), gap = new Object(), confirm = new Object();
            var s = selected(chooser);
            check(s.handoff(gap, type), "survival/creative handoff");
            check(s.close(gap), "default inventory close during TrMenu open");
            check(s.nameVisible(gap), "target name available while TrMenu renders");
            check(!s.visible(gap) && !s.prepare(), "render cannot prepare or pay");
            check(s.open(confirm), "TrMenu open");
            check(s.confirm(confirm), "bind actual menu");
            check(!s.handoff(gap, type), "duplicate delayed callback");
            check(!s.visible(chooser), "old chooser");
            check(s.visible(confirm), "confirmation visible");
            check(!s.consume(), "apply without prepare");
            check(s.prepare(), "prepare once");
            check(!s.prepare(), "duplicate prepare");
            check(s.consume(), "one execution");
            check(!s.consume(), "duplicate execution");
            check(s.close(confirm), "Bukkit identity does not identify TrMenu close");
        }
        for (var type : InventoryType.values()) {
            if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) {
                continue;
            }
            check(!selected(new Object()).handoff(new Object(), type), "other inventory " + type);
        }
        Object c = new Object(), v = new Object();
        var s = new ResetSelection();
        s.bind(c);
        check(!s.close(c), "cancel chooser");
        check(!s.select(v), "stale chooser callback");
        check(!s.handoff(v, InventoryType.CRAFTING), "no selection");
        s = selected(c);
        check(!s.open(v), "intervening open invalidates gap");
        check(s.handoff(v, InventoryType.CREATIVE), "handoff");
        check(s.open(c), "first open");
        check(!s.open(v), "reentrant second open");
        check(s.confirm(c), "callback binds session");
        check(!s.visible(v), "replaced session rejects");
        long[] now = { 0 };
        s = new ResetSelection(() -> now[0]);
        s.bind(c);
        now[0] = 60_000_000_000L;
        check(!s.select(c), "expired callback");
        now[0] = 0;
        s = new ResetSelection(() -> now[0]);
        s.bind(c);
        s.select(c);
        s.close(c);
        s.handoff(v, InventoryType.CREATIVE);
        s.open(c);
        s.confirm(c);
        s.prepare();
        now[0] = 60_000_000_000L;
        check(!s.visible(c) && !s.consume(), "expired prepared confirmation");
        System.out.println("Selection legal close/gap/creative/stale/cancel/reentry/expiry/once passed");
    }
}
