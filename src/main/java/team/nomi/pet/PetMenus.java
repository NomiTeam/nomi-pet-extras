package team.nomi.pet;

import java.lang.reflect.*;
import java.util.*;
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
import net.milkbowl.vault.economy.Economy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.*;

final class PetMenus implements Listener,CommandExecutor,MenuHandler<PetMenus.Flow> {
 static final MenuId<Flow> ID=MenuId.of("nuomi-reset-confirm",Flow.class);
 private final NomiPetExtras plugin;
 private final Economy economy;
 private final ConfirmationFlows<UUID,Flow> flows=new ConfirmationFlows<>(f->{f.payment.invalidate();f.ticket.invalidate();});
 private volatile boolean enabled=true;
 static final class Flow {
  final Player player;final Pet pet;final Skilltree previous;final UUID transaction=UUID.randomUUID();
  final ResetTransaction payment=new ResetTransaction();final MenuTicket ticket=new MenuTicket();final SelectionTransition selection=new SelectionTransition();final double amount;
  Skilltree target;Object holder;boolean opening,selected;
  Flow(Player p,Pet pet,double amount){player=p;this.pet=pet;previous=pet.getSkilltree();this.amount=amount;}
 }
 PetMenus(NomiPetExtras plugin,Economy e){this.plugin=plugin;economy=e;}
 void register(){MyPetApi.getGuiService().registerMenu(ID,this,()->plugin.getResource("reset-confirm.json"));}
 void shutdown(){enabled=false;flows.clear();}
 private void invalidate(Player p){flows.cancel(p.getUniqueId());}
 private boolean live(Flow f){return enabled && flows.get(f.player.getUniqueId())==f;}
 private boolean confirming(Flow f,Object holder){return enabled && f.holder==holder && flows.matches(f.player.getUniqueId(),f,f.ticket,holder,f.player.getOpenInventory().getTopInventory().getHolder(false),f.player.isOnline());}
 private void finish(Flow f,MenuInstance m){flows.finish(f.player.getUniqueId(),f,()->f.player.isOnline() && f.player.getOpenInventory().getTopInventory().getHolder(false)==m,m::close);}
 private double cost(){return Safety.cost(plugin.getConfig().getDouble("reset-cost",500));}
 private void tell(Player p,String s){p.sendMessage(Component.text(s));}
 /** Read-only view removes only the current tree for MyPet's NoSkilltree requirement. */
 static Pet preview(Pet pet){return (Pet)Proxy.newProxyInstance(Pet.class.getClassLoader(),new Class<?>[]{Pet.class},(p,m,a)->{
  if(m.getName().equals("getSkilltree"))return null;
  try{return m.invoke(pet,a);}catch(InvocationTargetException e){throw e.getCause();}
 });}
 private boolean route(Pet pet,Skilltree t){
  return Routes.base(pet.getLevel(),pet.getPetType(),t)
   && t.checkRequirements(preview(pet)) && MyPetApi.getSkilltreeManager().getSkilltree(t.getName())==t;
 }
 private boolean items(Pet pet){
  BackpackImpl b=pet.getSkills().get(BackpackImpl.class);
  // The state includes all stored slots, including slots beyond the current capacity.
  return b!=null && (b.isMenuOpen() || b.getState().map(s->Safety.hasItems(s.contents(),i->!i.getType().isAir())).orElse(true));
 }
 private boolean valid(Flow f){
  Skilltree t=f.target;if(t==null)return false;
  if(f.pet.getOwner().getPlayer()!=f.player || MyPetApi.getPetManager().getPet(f.player)!=f.pet || f.pet.getSkilltree()!=f.previous)return false;
  return Safety.allowed(f.pet.getOwner().getPlayer()==f.player,MyPetApi.getPetManager().getPet(f.player)==f.pet,
   f.pet.getSkilltree()==f.previous,f.player.hasPermission("MyPet.command.chooseskilltree")&&f.player.hasPermission("nomipetextras.reset"),
   items(f.pet),t.getRequiredLevel(),t.getInheritedSkilltreeName()!=null&&!t.getInheritedSkilltreeName().isBlank(),t.getMobTypes().isEmpty()||t.getMobTypes().contains(f.pet.getPetType()),route(f.pet,t));
 }
 private void start(Player p,Pet pet){
  invalidate(p);
  if(pet==null || pet.getOwner().getPlayer()!=p || MyPetApi.getPetManager().getPet(p)!=pet){tell(p,"只能重置本人当前宠物。");return;}
  if(!p.hasPermission("nomipetextras.reset")||!p.hasPermission("MyPet.command.chooseskilltree")){tell(p,"没有选择技能路线的权限。");return;}
  if(pet.getSkilltree()==null){tell(p,"请先为宠物选择技能路线。");p.performCommand("petchooseskilltree");return;}
  if(items(pet)){tell(p,"请先取出宠物背包中的全部物品。");return;}
  List<Skilltree> available=MyPetApi.getSkilltreeManager().getOrderedSkilltrees().stream().filter(t->t!=pet.getSkilltree()&&route(pet,t)).toList();
  if(available.isEmpty()){tell(p,"没有其他合法的基础技能路线。");return;}
  Flow f=new Flow(p,pet,cost());flows.put(p.getUniqueId(),f);
  ChooseSkilltreeContext context=new ChooseSkilltreeContext(p,pet,available,List.of(),target->{
   if(!f.selection.select(f.ticket,p.getOpenInventory().getTopInventory().getHolder(false),live(f),p.isOnline()))return;
   f.target=target;f.selected=true;
   // Native ChooseSkilltreeMenuHandler closes the chooser after this callback.
   p.getScheduler().runDelayed(plugin,task->{
    if(!live(f))return;
    Inventory current=p.getOpenInventory().getTopInventory();
    if(!valid(f) || !f.selection.advance(f.ticket,f.holder,current.getHolder(false),current.getType(),live(f),p.isOnline())){invalidate(p);return;}
    f.opening=true;
    try{MyPetApi.getGuiService().openMenu(p,ID,f);}finally{f.opening=false;}
   },null,1);
  });
  @SuppressWarnings("unchecked") MenuId<ChooseSkilltreeContext> choose=(MenuId<ChooseSkilltreeContext>)MenuIds.CHOOSE_SKILLTREE;
  f.opening=true;
  try{MyPetApi.getGuiService().openMenu(p,choose,context);f.holder=p.getOpenInventory().getTopInventory().getHolder(false);f.ticket.bind(f.holder);}
  catch(RuntimeException e){invalidate(p);throw e;}finally{f.opening=false;}
 }
 @EventHandler(priority=EventPriority.LOWEST) public void click(InventoryClickEvent e){
  if(!(e.getWhoClicked() instanceof Player p)||!(e.getView().getTopInventory().getHolder(false) instanceof MenuInstanceImpl m)
    || !(m.context() instanceof PetMenuContext context))return;
  Section section=m.definition().sections().get("nuomi-reset");
  if(!(section instanceof SlotSection s) || e.getRawSlot()!=s.row()*9+s.col())return;
  e.setCancelled(true);e.setResult(Event.Result.DENY);
  p.getScheduler().runDelayed(plugin,t->{if(enabled && p.getOpenInventory().getTopInventory().getHolder(false)==m)start(p,context.pet());},null,1);
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void open(InventoryOpenEvent e){
  if(!(e.getPlayer() instanceof Player p))return;
  Flow f=flows.get(p.getUniqueId());if(f!=null && !f.opening)invalidate(p);
  Inventory inv=e.getInventory();
  p.getScheduler().runDelayed(plugin,t->{
   if(!enabled || p.getOpenInventory().getTopInventory()!=inv || !(inv.getHolder(false) instanceof MenuInstanceImpl m) || !(m.context() instanceof PetMenuContext))return;
   if(!(m.definition().sections().get("nuomi-reset") instanceof SlotSection s))return;
   int n=s.row()*9+s.col();ItemStack item=inv.getItem(n);if(item==null)return;
   var meta=item.getItemMeta();meta.lore(List.of(Component.text("保留等级经验，重新计算基础路线技能"),Component.text("请先清空宠物背包"),Component.text("费用："+economy.format(cost()))));item.setItemMeta(meta);inv.setItem(n,item);
  },null,1);
 }
 @EventHandler public void close(InventoryCloseEvent e){
  if(!(e.getPlayer() instanceof Player p))return;Flow f=flows.get(p.getUniqueId());
  if(f!=null && !f.opening && e.getInventory().getHolder(false)==f.holder && !(f.selected && f.holder instanceof MenuInstanceImpl m && m.context() instanceof ChooseSkilltreeContext))invalidate(p);
 }
 @EventHandler public void quit(PlayerQuitEvent e){invalidate(e.getPlayer());}
 @Override public MenuId<Flow> id(){return ID;}
 @Override public void onOpen(MenuInstance m,Flow f){f.holder=m;f.ticket.bind(m);}
 @Override public TagResolver placeholders(Flow f,String section,int index){return TagResolver.resolver(
  Placeholder.unparsed("previous_name",f.previous.getDisplayName()),Placeholder.unparsed("target_name",f.target.getDisplayName()),Placeholder.unparsed("reset_price",economy.format(f.amount)));}
 @Override public void onClick(MenuInstance m,String section,ClickPayload payload){
  if(!enabled)return;
  if(!section.equals("confirm")&&!section.equals("cancel"))return;Flow f=(Flow)m.context();Player p=f.player;
  p.getScheduler().runDelayed(plugin,t->{
   if(!confirming(f,m))return;
   if(section.equals("cancel")){invalidate(p);if(!m.popBack()){m.close();start(p,f.pet);}return;}
   if(!valid(f)){finish(f,m);tell(p,"宠物、路线、权限或背包状态已变化，请重新选择。");return;}
   String trace="tx="+f.transaction+" player="+p.getUniqueId()+" pet="+f.pet.getUUID()+" amount="+f.amount+" from="+f.previous.getName()+" to="+f.target.getName();
   var result=f.payment.commit(()->{
    plugin.getLogger().info("RESET DEBIT_ATTEMPT "+trace);
    return economy.withdrawPlayer(p,f.amount).transactionSuccess();
   },()->apply(f,m),()->economy.depositPlayer(p,f.amount).transactionSuccess(),ex->plugin.getLogger().log(java.util.logging.Level.SEVERE,"RESET exception "+trace,ex));
   plugin.getLogger().log(result==ResetTransaction.Result.APPLICATION_UNKNOWN||result==ResetTransaction.Result.REFUND_FAILED||result==ResetTransaction.Result.PAYMENT_UNKNOWN?java.util.logging.Level.SEVERE:java.util.logging.Level.INFO,"RESET "+result+" "+trace);
   if(result==ResetTransaction.Result.USED)return;
   finish(f,m);
   tell(p,switch(result){
    case SUCCESS->"技能路线已重置，保留等级经验。扣除 "+economy.format(f.amount);
    case PAYMENT_FAILED->"扣费失败，未应用重置。";
    case REFUNDED->"重置未成功，费用已退回。";
    case REFUND_FAILED->"重置未成功且退款失败，请联系管理员。记录号："+f.transaction;
    case APPLICATION_UNKNOWN->"宠物应用结果不明，未自动退款或回滚，请管理员核查宠物及账目。记录号："+f.transaction;
    case PAYMENT_UNKNOWN->"支付接口异常，结果需管理员核查。记录号："+f.transaction;
    default->"此确认已处理。";
   });
  },null,1);
 }
 private ResetTransaction.Application apply(Flow f,Object holder){
  if(!confirming(f,holder) || !valid(f) || !confirming(f,holder))return ResetTransaction.Application.NO_CHANGE;
  return ResetApplication.apply(f.pet,f.target);
 }
 @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!(sender instanceof Player p)){sender.sendMessage("NomiPetExtras enabled="+enabled+" pending="+flows.size()+" cost="+cost()+"; player-only reset");return true;}
  p.getScheduler().run(plugin,t->start(p,MyPetApi.getPetManager().getPet(p)),null);return true;
 }
}
