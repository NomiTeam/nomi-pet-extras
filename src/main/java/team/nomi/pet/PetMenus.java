package team.nomi.pet;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.entity.Pet;
import de.Keyle.MyPet.api.gui.*;
import de.Keyle.MyPet.api.skill.skilltree.Skilltree;
import de.Keyle.MyPet.gui.MenuInstanceImpl;
import de.Keyle.MyPet.gui.context.*;
import de.Keyle.MyPet.skill.skills.BackpackImpl;
import net.kyori.adventure.text.Component;

final class PetMenus implements Listener, CommandExecutor {

    private final NomiPetExtras plugin;

    private final Map<UUID, Flow> flows = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;

    static final class Flow {

        final Player player;

        final UUID playerId;

        final Pet pet;

        final Skilltree previous;

        final ResetSelection state = new ResetSelection();

        Skilltree target;

        TrMenuConfirm.Window confirmation;

        boolean opening, checking;

        Flow(Player player, Pet pet) {
            this.player = player;
            playerId = player.getUniqueId();
            this.pet = pet;
            previous = pet.getSkilltree();
        }
    }

    PetMenus(NomiPetExtras plugin) {
        this.plugin = plugin;
    }

    void shutdown() {
        enabled = false;
        flows.clear();
    }

    private boolean owned(Player p) {
        return plugin.getServer().isOwnedByCurrentRegion(p);
    }

    private Inventory current(Player p) {
        return p.getOpenInventory().getTopInventory();
    }

    private void invalidate(Player p) {
        flows.remove(p.getUniqueId());
    }

    private void discard(Flow f) {
        flows.remove(f.playerId, f);
    }

    private boolean live(Flow f) {
        return enabled && flows.get(f.player.getUniqueId()) == f && f.player.isOnline();
    }

    private void tell(Player p, String text) {
        p.sendMessage(Component.text(text));
    }

    private boolean ready(Flow f) {
        return ready(f, false);
    }

    private boolean ready(Flow f, boolean display) {
        if (f == null || f.checking) {
            return false;
        }
        f.checking = true;
        try {
            return live(f) && visible(f, display) && valid(f) && live(f) && visible(f, display);
        } finally {
            f.checking = false;
        }
    }

    private boolean visible(Flow f, boolean display) {
        TrMenuConfirm.Window window = f.confirmation;
        if (window == null) {
            return display && f.state.nameVisible(current(f.player));
        }
        return display && f.state.opening() ? window.rendering() : window.current() && f.state.visible(window);
    }

    boolean prepare(Player p) {
        if (!owned(p)) {
            return false;
        }
        Flow f = flows.get(p.getUniqueId());
        if (f != null && f.checking) {
            return false;
        }
        try {
            if (!ready(f)) {
                if (f != null) {
                    discard(f);
                }
                return false;
            }
            return f.state.prepare();
        } catch (RuntimeException | LinkageError error) {
            if (f != null) {
                discard(f);
            }
            return false;
        }
    }

    String apply(Player p) {
        if (!owned(p)) {
            return "REJECTED";
        }
        Flow f = flows.get(p.getUniqueId());
        if (f == null || f.checking || !f.state.consume()) {
            return "REJECTED";
        }
        try {
            if (!ready(f)) {
                return "REJECTED";
            }
            // 进入 MyPet 前移除本次选择，防止同步技能事件重入重复执行。
            if (!flows.remove(p.getUniqueId(), f)) {
                return "REJECTED";
            }
            return ResetApplication.apply(f.pet, f.target);
        } catch (RuntimeException | LinkageError error) {
            return "REJECTED";
        } finally {
            discard(f);
        }
    }

    void cancel(Player p) {
        if (owned(p)) {
            invalidate(p);
        }
    }

    String getTargetName(Player p) {
        if (!owned(p)) {
            return "";
        }
        Flow f = flows.get(p.getUniqueId());
        try {
            return ready(f, true) ? f.target.getDisplayName() : "";
        } catch (RuntimeException | LinkageError error) {
            return "";
        }
    }

    /**
     * Read-only view removes only the current tree for MyPet's NoSkilltree requirement.
     */
    static Pet preview(Pet pet) {
        return (Pet) Proxy.newProxyInstance(Pet.class.getClassLoader(), new Class<?>[] { Pet.class }, (p, m, a) -> {
            if (m.getName().equals("getSkilltree")) {
                return null;
            }
            try {
                return m.invoke(pet, a);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    private boolean route(Pet pet, Skilltree t) {
        // 原生条件可同步调用其他插件；回调后重查等级、类型和基础路线约束。
        return Routes.base(pet.getLevel(), pet.getPetType(), t)
                && t.checkRequirements(preview(pet))
                && Routes.base(pet.getLevel(), pet.getPetType(), t)
                && MyPetApi.getSkilltreeManager().getSkilltree(t.getName()) == t;
    }

    private boolean items(Pet pet) {
        BackpackImpl b = pet.getSkills().get(BackpackImpl.class);
        // 检查全部持久槽，包括当前背包容量以外仍保存物品的槽位。
        return b != null && (b.isMenuOpen() || b.getState().map(s -> Safety.hasItems(s.contents(), i -> !i.getType().isAir())).orElse(true));
    }

    private boolean valid(Flow f) {
        Skilltree target = f.target;
        if (target == null || target == f.previous) {
            return false;
        }
        // 原生条件可能重入其他插件；回调后再检查归属、当前宠物和原路线。
        if (!route(f.pet, target)) {
            return false;
        }
        boolean owner = f.pet.getOwner().getPlayer() == f.player;
        boolean active = MyPetApi.getPetManager().getPet(f.player) == f.pet;
        boolean unchanged = f.pet.getSkilltree() == f.previous;
        boolean permission = f.player.hasPermission("MyPet.command.chooseskilltree") && f.player.hasPermission("nomipetextras.reset");
        String parent = target.getInheritedSkilltreeName();
        return Safety.allowed(owner, active, unchanged, permission, items(f.pet),
                target.getRequiredLevel(), parent != null && !parent.isBlank(),
                target.getMobTypes().isEmpty() || target.getMobTypes().contains(f.pet.getPetType()), true);
    }

    private void start(Player p, Pet pet) {
        if (!owned(p) || !enabled || !p.isOnline()) {
            return;
        }
        invalidate(p);
        if (pet == null || pet.getOwner().getPlayer() != p || MyPetApi.getPetManager().getPet(p) != pet) {
            tell(p, "只能重置本人当前宠物。");
            return;
        }
        if (!p.hasPermission("nomipetextras.reset") || !p.hasPermission("MyPet.command.chooseskilltree")) {
            tell(p, "没有选择技能路线的权限。");
            return;
        }
        if (pet.getSkilltree() == null) {
            tell(p, "请先为宠物选择技能路线。");
            p.performCommand("petchooseskilltree");
            return;
        }
        if (items(pet)) {
            tell(p, "请先取出宠物背包中的全部物品。");
            return;
        }
        Flow f = new Flow(p, pet);
        flows.put(p.getUniqueId(), f);
        p.getScheduler().runDelayed(plugin, task -> discard(f), null, 1200);
        List<Skilltree> available;
        try {
            available = MyPetApi.getSkilltreeManager().getOrderedSkilltrees().stream()
                    .filter(t -> t != f.previous && route(pet, t))
                    .toList();
        } catch (RuntimeException | LinkageError error) {
            discard(f);
            return;
        }
        if (!live(f)) {
            return;
        }
        if (available.isEmpty()) {
            discard(f);
            tell(p, "没有其他合法的基础技能路线。");
            return;
        }
        ChooseSkilltreeContext context = new ChooseSkilltreeContext(p, pet, available, List.of(), target -> {
            if (!owned(p) || !live(f) || !f.state.select(current(p))) {
                return;
            }
            f.target = target;
            // MyPet 在选择回调返回后原生关闭；下一实体 tick 才交给 TrMenu。
            p.getScheduler().runDelayed(plugin, task -> openConfirmation(f), null, 1);
        });
        @SuppressWarnings("unchecked")
        MenuId<ChooseSkilltreeContext> choose = (MenuId<ChooseSkilltreeContext>) MenuIds.CHOOSE_SKILLTREE;
        f.opening = true;
        try {
            MyPetApi.getGuiService().openMenu(p, choose, context);
            Inventory opened = current(p);
            if (live(f) && opened.getHolder(false) instanceof MenuInstanceImpl menu && menu.context() == context) {
                f.state.bind(opened);
            } else {
                discard(f);
            }
        } catch (RuntimeException error) {
            discard(f);
            throw error;
        } finally {
            f.opening = false;
        }
    }

    private void openConfirmation(Flow f) {
        if (!live(f)) {
            return;
        }
        Player player = f.player;
        Inventory inventory = current(player);
        try {
            if (!valid(f) || !live(f) || !f.state.handoff(inventory, inventory.getType())) {
                discard(f);
                return;
            }
            boolean opened = TrMenuConfirm.open(plugin, player, window -> {
                if (owned(player) && live(f)) {
                    f.confirmation = window;
                }
            }, () -> discard(f));
            if (!opened || !live(f) || f.confirmation == null
                    || !f.confirmation.current() || !f.state.confirm(f.confirmation)) {
                discard(f);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            discard(f);
            tell(player, "确认菜单暂时不可用，请重新选择。");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder(false) instanceof MenuInstanceImpl m) || !(m.context() instanceof PetMenuContext context)) {
            return;
        }
        Section section = m.definition().sections().get("nuomi-reset");
        if (!(section instanceof SlotSection s) || e.getRawSlot() != s.row() * 9 + s.col()) {
            return;
        }
        e.setCancelled(true);
        e.setResult(Event.Result.DENY);
        p.getScheduler().runDelayed(plugin, t -> {
            if (enabled && current(p).getHolder(false) == m) {
                start(p, context.pet());
            }
        }, null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void open(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) {
            return;
        }
        Flow f = flows.get(p.getUniqueId());
        if (f != null && !f.opening && !f.state.open(e.getInventory())) {
            discard(f);
        }
        Inventory inv = e.getInventory();
        p.getScheduler().runDelayed(plugin, t -> {
            if (!enabled || current(p) != inv || !(inv.getHolder(false) instanceof MenuInstanceImpl m) || !(m.context() instanceof PetMenuContext)) {
                return;
            }
            if (!(m.definition().sections().get("nuomi-reset") instanceof SlotSection s)) {
                return;
            }
            int n = s.row() * 9 + s.col();
            ItemStack item = inv.getItem(n);
            if (item == null) {
                return;
            }
            var meta = item.getItemMeta();
            meta.lore(List.of(Component.text("保留等级经验，重新计算基础路线技能"), Component.text("请先清空宠物背包"), Component.text("费用见确认菜单")));
            item.setItemMeta(meta);
            inv.setItem(n, item);
        }, null, 1);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) {
            return;
        }
        Flow f = flows.get(p.getUniqueId());
        if (f != null && !f.opening && !f.state.close(e.getInventory())) {
            discard(f);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        invalidate(e.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("NomiPetExtras: player-only route selection");
            return true;
        }
        p.getScheduler().run(plugin, t -> start(p, MyPetApi.getPetManager().getPet(p)), null);
        return true;
    }
}
