package team.nomi.pet;

import java.util.function.LongSupplier;
import org.bukkit.event.inventory.InventoryType;

/**
 * One native choice and one short lived confirmation, confined to the player's entity thread.
 */
final class ResetSelection {

    private enum Phase {

        CHOOSING,
        SELECTED,
        OPENING,
        CONFIRM,
        PREPARED,
        CONSUMED
    }

    private final LongSupplier clock;

    private final long expires;

    private Phase phase = Phase.CHOOSING;

    private Object chooser, gap, confirmation;

    private boolean chooserClosed, gapClosed, bukkitOpened;

    ResetSelection() {
        this(System::nanoTime);
    }

    ResetSelection(LongSupplier clock) {
        this.clock = clock;
        expires = clock.getAsLong() + 60_000_000_000L;
    }

    private boolean fresh() {
        return clock.getAsLong() - expires < 0;
    }

    void bind(Object inventory) {
        chooser = inventory;
    }

    boolean select(Object current) {
        if (!fresh() || phase != Phase.CHOOSING || chooser == null || current != chooser) {
            return false;
        }
        phase = Phase.SELECTED;
        return true;
    }

    boolean close(Object inventory) {
        if (inventory == chooser && phase == Phase.SELECTED && !chooserClosed) {
            chooserClosed = true;
            return true;
        }
        if (phase == Phase.OPENING && inventory == gap && !gapClosed) {
            gapClosed = true;
            return true;
        }
        if (inventory == chooser && phase != Phase.CHOOSING && phase != Phase.SELECTED) {
            return true;
        }
        // TrMenu packet 关闭由本次 receptacle 回调处理，不能用 Bukkit 库存身份推断。
        return inventory != chooser;
    }

    boolean handoff(Object current, InventoryType type) {
        if (!fresh() || phase != Phase.SELECTED || !chooserClosed || current == chooser
                || (type != InventoryType.CRAFTING && type != InventoryType.CREATIVE)) {
            return false;
        }
        gap = current;
        phase = Phase.OPENING;
        return true;
    }

    boolean open(Object inventory) {
        if (!fresh() || phase != Phase.OPENING || bukkitOpened) {
            return false;
        }
        bukkitOpened = true;
        return true;
    }

    boolean confirm(Object session) {
        if (!fresh() || phase != Phase.OPENING || session == null) {
            return false;
        }
        confirmation = session;
        phase = Phase.CONFIRM;
        return true;
    }

    boolean visible(Object current) {
        return fresh() && confirmation != null && current == confirmation
                && (phase == Phase.CONFIRM || phase == Phase.PREPARED || phase == Phase.CONSUMED);
    }

    boolean opening() {
        return fresh() && phase == Phase.OPENING;
    }

    boolean nameVisible(Object current) {
        return visible(current) || opening() && current == gap;
    }

    boolean prepare() {
        if (!fresh() || phase != Phase.CONFIRM) {
            return false;
        }
        phase = Phase.PREPARED;
        return true;
    }

    boolean consume() {
        if (!fresh() || phase != Phase.PREPARED) {
            return false;
        }
        phase = Phase.CONSUMED;
        return true;
    }
}
