package team.nomi.pet;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.plugin.java.JavaPlugin;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.entity.*;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.plugin.MyPetPlugin;
import de.Keyle.MyPet.api.repository.PetManager;
import de.Keyle.MyPet.api.skill.Skills;
import de.Keyle.MyPet.api.skill.skills.Backpack;
import de.Keyle.MyPet.api.skill.skilltree.*;
import de.Keyle.MyPet.api.util.service.*;
import de.Keyle.MyPet.skill.skills.BackpackImpl;

/**
 * Fault adapters use real API types; decisions run through public production bridge methods.
 */
public class BridgeSafetyTest extends MyPetApi {

    static Object unsafe;

    static Method allocate;

    static <T> T shell(Class<T> type) throws Exception {
        return type.cast(allocate.invoke(unsafe, type));
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    static void field(Class<?> type, Object object, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        f.set(object, value);
    }

    static void check(boolean b, String m) {
        if (!b) {
            throw new AssertionError(m);
        }
    }

    static final class Manager extends PetManager {

        Pet current;

        @Override
        public Pet getPet(Player p) {
            return current;
        }

        public PersistedPet snapshot(Pet p) {
            throw new UnsupportedOperationException();
        }

        public Optional<Pet> activatePet(StoredPet p) {
            throw new UnsupportedOperationException();
        }

        public boolean deactivatePet(MyPetPlayer p, Pet pet, boolean b) {
            throw new UnsupportedOperationException();
        }

        public CompletableFuture<List<StoredPet>> getStoredPets(MyPetPlayer p) {
            throw new UnsupportedOperationException();
        }

        public CompletableFuture<Void> refreshOwnership(MyPetPlayer p) {
            throw new UnsupportedOperationException();
        }
    }

    static final class PetSkills extends Skills {

        PetSkills() {
            super(null);
        }

        BackpackImpl backpack;

        @Override
        public <T extends Skill> T get(Class<T> type) {
            return backpack == null ? null : type.cast(backpack);
        }
    }

    static final class Target extends Skilltree {

        Runnable requirement = () -> {
        };

        boolean allowed = true;

        Target() {
            super("target");
        }

        @Override
        public boolean checkRequirements(Pet pet) {
            check(pet.getSkilltree() == null, "native no-skilltree preview");
            requirement.run();
            return allowed;
        }
    }

    static Manager manager = new Manager();

    static SkilltreeManager trees = new SkilltreeManager();

    static final class TestItem extends ItemStack {

        final org.bukkit.Material material;

        TestItem(org.bukkit.Material m) {
            material = m;
        }

        @Override
        public org.bukkit.Material getType() {
            return material;
        }
    }

    public static void main(String[] args) throws Exception {
        var type = Class.forName("sun.misc.Unsafe");
        var f = type.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = f.get(null);
        allocate = type.getMethod("allocateInstance", Class.class);
        ServiceManager services = new ServiceManager() {

            @Override
            public <T extends ServiceContainer> Optional<T> getService(Class<? extends T> type) {
                return Optional.of(type.cast(trees));
            }
        };
        var mypet = shell(de.Keyle.MyPet.MyPetPlugin.class);
        field(de.Keyle.MyPet.MyPetPlugin.class, mypet, "petManager", manager);
        field(de.Keyle.MyPet.MyPetPlugin.class, mypet, "serviceManager", services);
        setPlugin(mypet);
        for (String mode : new String[] { "owner", "apply-owner", "apply-previous", "requirement-level",
                "normal",
                "close",
                "cancel",
                "pet",
                "previous",
                "permission",
                "requirements",
                "unregistered",
                "requirement-prepare",
                "requirement-switch",
                "requirement-cancel",
                "apply-pet",
                "apply-permission",
                "once",
                "reentry",
                "backpack-empty",
                "backpack-hidden",
                "backpack-air",
                "backpack-open",
                "apply-backpack",
                "apply-backpack-open",
                "old-close-new-flow",
                "old-close-prepared",
                "window-switch",
                "session-switch",
                "expired-prepare",
                "expired-apply",
                "requirement-newflow" }) {
            run(mode);
        }
        System.out.println("Public bridge prepare/apply/cancel: pet/route/permission/native/reentrant/once and real BackpackImpl persisted 54 slots, air, empty, open and prepare-to-apply changes passed");
    }

    static void run(String mode) throws Exception {
        UUID id = UUID.randomUUID();
        boolean[] permission = { true };
        int[] applied = { 0 };
        int[] level = { 20 };
        Inventory inv = proxy(Inventory.class, (p, m, a) -> {
            throw new AssertionError(m.getName());
        });
        Player[] viewer = { null };
        Player[] ownerPlayer = { null };
        InventoryView view = proxy(InventoryView.class, (p, m, a) -> {
            if (m.getName().equals("getTopInventory")) {
                return inv;
            }
            if (m.getName().equals("getPlayer")) {
                return viewer[0];
            }
            throw new AssertionError(m.getName());
        });
        Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "hasPermission" -> permission[0];
            case "getOpenInventory" -> throw new AssertionError("TrMenu confirmation read Bukkit top inventory");
            default -> throw new AssertionError(m.getName());
        });
        viewer[0] = player;
        ownerPlayer[0] = player;
        MyPetPlayer owner = proxy(MyPetPlayer.class, (p, m, a) -> {
            if (m.getName().equals("getPlayer")) {
                return ownerPlayer[0];
            }
            throw new AssertionError(m.getName());
        });
        var previous = new Skilltree("previous");
        var target = new Target();
        trees.registerSkilltree(target);
        Skilltree[] current = { previous };
        var skills = shell(PetSkills.class);
        PetType petType = PetType.register("test" + mode, Pet.class);
        NomiPetExtras plugin = shell(NomiPetExtras.class);
        Pet pet = proxy(Pet.class, (p, m, a) -> switch (m.getName()) {
            case "getSkilltree" -> current[0];
            case "getOwner" -> owner;
            case "getLevel" -> level[0];
            case "getExp" -> 123.0;
            case "getPetType" -> petType;
            case "getSkills" -> skills;
            case "setSkilltree" -> {
                applied[0]++;
                current[0] = target;
                if (mode.equals("reentry")) {
                    check(plugin.apply(player).equals("REJECTED"), "reentrant apply");
                }
                yield true;
            }
            default -> throw new AssertionError(m.getName());
        });
        if (mode.contains("backpack")) {
            skills.backpack = new BackpackImpl(pet);
            ItemStack[] persisted = new ItemStack[54];
            if (mode.equals("backpack-hidden")) {
                persisted[53] = new TestItem(org.bukkit.Material.STONE);
            }
            if (mode.equals("backpack-air")) {
                persisted[53] = new TestItem(org.bukkit.Material.AIR);
            }
            skills.backpack.applyState(new Backpack.State(persisted));
            check(skills.backpack.getRows().getValue().intValue() == 0, "zero active rows");
            check(skills.backpack.getState().orElseThrow().contents().length == 54, "real persisted slots retained beyond rows");
            if (mode.equals("backpack-open")) {
                skills.backpack.setMenuOpen(true);
            }
        }
        manager.current = pet;
        Server server = proxy(Server.class, (p, m, a) -> {
            if (m.getName().equals("isOwnedByCurrentRegion")) {
                return true;
            }
            throw new AssertionError(m.getName());
        });
        field(JavaPlugin.class, plugin, "server", server);
        PetMenus menus = new PetMenus(plugin);
        field(NomiPetExtras.class, plugin, "menus", menus);
        Field ff = PetMenus.class.getDeclaredField("flows");
        ff.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, PetMenus.Flow> flows = (Map<UUID, PetMenus.Flow>) ff.get(menus);
        var flow = new PetMenus.Flow(player, pet);
        flow.target = target;
        long[] now = { 0 };
        field(PetMenus.Flow.class, flow, "state", new ResetSelection(() -> now[0]));
        openFlow(flow, flows, player);
        trplugins.menu.module.display.Menu.Window captured = null;
        if (mode.equals("old-close-new-flow") || mode.equals("old-close-prepared")) {
            var oldWindow = trplugins.menu.module.display.MenuSession.SESSIONS.get(id).receptacle;
            var replacement = new PetMenus.Flow(player, pet);
            replacement.target = target;
            openFlow(replacement, flows, player);
            captured = oldWindow;
            if (mode.equals("old-close-new-flow")) {
                oldWindow.close(player);
            }
            check(flows.get(id) == replacement && (mode.equals("old-close-prepared") || !replacement.confirmation.current()),
                    "native late close invalidates replacement; business must reject");
        }
        switch (mode) {
            case "owner" -> ownerPlayer[0] = null;
            case "requirement-level" -> {
                target.setRequiredLevel(1);
                target.requirement = () -> level[0] = 0;
            }
            case "window-switch" ->
                trplugins.menu.module.display.MenuSession.SESSIONS.get(id).receptacle = new trplugins.menu.module.display.Menu.Window();
            case "session-switch" ->
                trplugins.menu.module.display.MenuSession.SESSIONS.put(id, new trplugins.menu.module.display.MenuSession(player));
            case "expired-prepare" -> now[0] = 60_000_000_000L;
            case "requirement-newflow" -> target.requirement = () -> flows.put(id, new PetMenus.Flow(player, pet));
            case "close" -> trplugins.menu.module.display.MenuSession.SESSIONS.get(id).receptacle.close(player);
            case "cancel" -> plugin.cancel(player);
            case "pet" -> manager.current = null;
            case "previous" -> current[0] = target;
            case "permission" -> permission[0] = false;
            case "requirements" -> target.allowed = false;
            case "unregistered" -> trees.clearSkilltrees();
            case "requirement-prepare" ->
                target.requirement = () -> check(!plugin.prepare(player), "reentrant prepare must reject");
            case "requirement-switch" -> target.requirement = () -> manager.current = null;
            case "requirement-cancel" -> target.requirement = () -> plugin.cancel(player);
        }
        boolean normal = Set.of("apply-owner", "apply-previous", "normal",
                "once",
                "reentry",
                "requirement-prepare",
                "apply-pet",
                "apply-permission",
                "backpack-empty",
                "backpack-air",
                "apply-backpack",
                "apply-backpack-open",
                "old-close-prepared",
                "expired-apply").contains(mode);
        check(plugin.prepare(player) == normal, mode + " prepare");
        check(applied[0] == 0, "prepare mutated pet");
        if (mode.equals("expired-apply")) {
            now[0] = 60_000_000_000L;
        }
        if (mode.equals("requirement-newflow")) {
            check(flows.containsKey(id), "condition reentry preserves replacement flow");
        }
        if (mode.equals("apply-owner")) {
            ownerPlayer[0] = null;
        }
        if (mode.equals("apply-previous")) {
            current[0] = target;
        }
        if (mode.equals("apply-pet")) {
            manager.current = null;
        }
        if (mode.equals("apply-permission")) {
            permission[0] = false;
        }
        if (mode.equals("apply-backpack")) {
            ItemStack[] slots = new ItemStack[54];
            slots[53] = new TestItem(org.bukkit.Material.STONE);
            skills.backpack.applyState(new Backpack.State(slots));
        }
        if (mode.equals("apply-backpack-open")) {
            skills.backpack.setMenuOpen(true);
        }
        if (mode.equals("old-close-prepared")) {
            captured.close(player);
            check(plugin.getTargetName(player).isEmpty(), "closed replacement has no actionable target");
        }
        boolean success = Set.of("normal", "once", "reentry", "requirement-prepare", "backpack-empty", "backpack-air").contains(mode);
        check(plugin.apply(player).equals(success ? "SUCCESS" : "REJECTED"), mode + " apply");
        check(applied[0] == (success ? 1 : 0), mode + " mutation count");
        check(plugin.apply(player).equals("REJECTED"), mode + " repeated apply");
    }

    static void openFlow(PetMenus.Flow flow, Map<UUID, PetMenus.Flow> flows, Player player) throws Exception {
        Object chooser = new Object();
        flow.state.bind(chooser);
        flow.state.select(chooser);
        flow.state.close(chooser);
        flow.state.handoff(new Object(), InventoryType.CRAFTING);
        flows.put(player.getUniqueId(), flow);
        check(TrMenuConfirm.open(new trplugins.menu.module.display.Menu(), player, w -> flow.confirmation = w, "trplugins.menu"), "packet open");
        flow.state.confirm(flow.confirmation);
    }
}
