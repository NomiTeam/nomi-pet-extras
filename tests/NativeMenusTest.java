package team.nomi.pet;

import de.Keyle.MyPet.api.entity.Pet;
import de.Keyle.MyPet.api.gui.ClickPayload;
import de.Keyle.MyPet.api.gui.MenuDefinition;
import de.Keyle.MyPet.api.gui.MenuInstance;
import de.Keyle.MyPet.api.gui.SlotSection;
import de.Keyle.MyPet.api.skill.skilltree.Skilltree;
import de.Keyle.MyPet.gui.MenuInstanceImpl;
import de.Keyle.MyPet.gui.context.ChooseSkilltreeContext;
import de.Keyle.MyPet.gui.context.PetMenuContext;
import de.Keyle.MyPet.gui.menus.ChooseSkilltreeMenuHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/** 真实 MyPet handler 和 Bukkit 事件；离线库存替身不模拟服务端网络。 */
public class NativeMenusTest {
    public static void main(String[] args) throws Exception {
        nativeChoice();
        nativeRequirements();
        unrelatedSlots();
        System.out.println("Real MyPet chooser callback/close order and unrelated reset/skilltrees slots passed");
    }

    private static void nativeChoice() {
        List<String> calls = new ArrayList<>();
        Skilltree target = new Skilltree("base");
        ChooseSkilltreeContext context = new ChooseSkilltreeContext(
                null, null, List.of(target), List.of(), selected -> {
                    BridgeSafetyTest.check(selected == target, "native target identity");
                    calls.add("choose");
                });
        MenuInstance menu = BridgeSafetyTest.proxy(MenuInstance.class, (object, method, values) -> {
            return switch (method.getName()) {
                case "context" -> context;
                case "close" -> {
                    calls.add("close");
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            };
        });
        var handler = new ChooseSkilltreeMenuHandler();
        var click = new ClickPayload(0, 0, ClickType.LEFT, false, null);
        handler.onClick(menu, "reset", click);
        BridgeSafetyTest.check(calls.isEmpty(), "non-target section remains native no-op");
        handler.onClick(menu, "skilltrees", new ClickPayload(0, -1, ClickType.LEFT, false, null));
        BridgeSafetyTest.check(calls.isEmpty(), "native invalid selection");
        handler.onClick(menu, "skilltrees", click);
        BridgeSafetyTest.check(calls.equals(List.of("choose", "close")), "native callback precedes close");
    }

    private static void nativeRequirements() {
        Skilltree previous = new Skilltree("previous");
        Pet pet = BridgeSafetyTest.proxy(Pet.class, (object, method, values) -> {
            return switch (method.getName()) {
                case "getSkilltree" -> previous;
                case "getLevel" -> 20;
                default -> throw new AssertionError(method.getName());
            };
        });
        var requirement = new de.Keyle.MyPet.skill.skilltree.requirements.NoSkilltreeRequirement();
        BridgeSafetyTest.check(!requirement.check(previous, pet, null), "real native NoSkilltree rejects current tree");
        Pet preview = PetMenus.preview(pet);
        BridgeSafetyTest.check(requirement.check(previous, preview, null), "preview masks only tree");
        BridgeSafetyTest.check(preview.getLevel() == 20 && pet.getSkilltree() == previous,
                "preview forwards level without changing real tree");
    }

    private static void unrelatedSlots() throws Exception {
        var unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        BridgeSafetyTest.unsafe = unsafeField.get(null);
        BridgeSafetyTest.allocate = unsafeClass.getMethod("allocateInstance", Class.class);
        MenuInstanceImpl holder = BridgeSafetyTest.shell(MenuInstanceImpl.class);
        // 仅 nuomi-reset 槽属于本插件；reset、skilltrees 和下方玩家库存均不拦截。
        var item = new de.Keyle.MyPet.api.gui.ItemAppearance(
                org.bukkit.Material.STONE, "Reset", List.of(), false, 1, 0, null, null);
        var reset = new SlotSection("nuomi-reset", null, 4, 1, item, null, null, null, false);
        var definition = new MenuDefinition("pet-menu", "Pet", 3, true,
                null, null, null, Map.of("nuomi-reset", reset));
        Player player = BridgeSafetyTest.proxy(Player.class, (object, method, values) -> {
            throw new AssertionError("unrelated slot accessed player " + method.getName());
        });
        BridgeSafetyTest.field(MenuInstanceImpl.class, holder, "definition", definition);
        BridgeSafetyTest.field(MenuInstanceImpl.class, holder, "context", new PetMenuContext(player, null));
        Inventory inventory = BridgeSafetyTest.proxy(Inventory.class, (object, method, values) -> {
            return switch (method.getName()) {
                case "getHolder" -> holder;
                case "getSize" -> 27;
                default -> throw new AssertionError(method.getName());
            };
        });
        InventoryView view = BridgeSafetyTest.proxy(InventoryView.class, (object, method, values) -> {
            return switch (method.getName()) {
                case "getTopInventory" -> inventory;
                case "getPlayer" -> player;
                case "convertSlot" -> values[0];
                default -> throw new AssertionError(method.getName());
            };
        });
        PetMenus bridge = new PetMenus(null);
        for (int slot : new int[] {0, 12, 14, 27, -999}) {
            var event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                    slot, ClickType.LEFT, InventoryAction.NOTHING);
            bridge.click(event);
            BridgeSafetyTest.check(!event.isCancelled(), "unrelated slot " + slot);
        }
        // 其他原生上下文即使占用同一坐标，也不能被接管。
        BridgeSafetyTest.field(MenuInstanceImpl.class, holder, "context", new Object());
        var other = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                13, ClickType.LEFT, InventoryAction.NOTHING);
        bridge.click(other);
        BridgeSafetyTest.check(!other.isCancelled(), "other context reset untouched");
    }
}
