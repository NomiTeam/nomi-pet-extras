package team.nomi.pet;

import org.bukkit.event.inventory.InventoryType;

/** Executes the production chooser callback/deferred-open decision, using real Bukkit types. */
public class SelectionTransitionTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static final class Flow {
  final Object chooser=new Object();final MenuTicket ticket=new MenuTicket();
  final SelectionTransition selection=new SelectionTransition();
  Flow(){ticket.bind(chooser);}
 }
 public static void main(String[] args){
  for(InventoryType type:new InventoryType[]{InventoryType.CRAFTING,InventoryType.CREATIVE}){
   Flow f=new Flow();
   check(f.selection.select(f.ticket,f.chooser,true,true),"selection rejected");
   check(!f.selection.select(f.ticket,f.chooser,true,true),"duplicate callback accepted");
   check(f.selection.advance(f.ticket,f.chooser,null,type,true,true),type+" default must open confirmation");
   check(!f.selection.advance(f.ticket,f.chooser,null,type,true,true),"duplicate deferred open");
  }
  for(InventoryType type:InventoryType.values()){
   if(type==InventoryType.CRAFTING || type==InventoryType.CREATIVE)continue;
   Flow f=new Flow();f.selection.select(f.ticket,f.chooser,true,true);
   check(!f.selection.advance(f.ticket,f.chooser,new Object(),type,true,true),"other menu accepted: "+type);
  }
  for(String mode:new String[]{"unselected","still-chooser","stale","offline","ticket","rebound"}){
   Flow f=new Flow();
   if(!mode.equals("unselected"))f.selection.select(f.ticket,f.chooser,true,true);
   if(mode.equals("ticket"))f.ticket.invalidate();
   if(mode.equals("rebound"))f.ticket.bind(new Object());
   check(!f.selection.advance(f.ticket,f.chooser,mode.equals("still-chooser")?f.chooser:null,
    InventoryType.CREATIVE,!mode.equals("stale"),!mode.equals("offline")),mode+" opened");
  }
  for(String mode:new String[]{"stale","offline","wrong-holder","ticket"}){
   Flow f=new Flow();if(mode.equals("ticket"))f.ticket.invalidate();
   check(!f.selection.select(f.ticket,mode.equals("wrong-holder")?new Object():f.chooser,
    !mode.equals("stale"),!mode.equals("offline")),mode+" callback accepted");
  }
  ConfirmationFlows<String,Flow> flows=new ConfirmationFlows<>(f->f.ticket.invalidate());
  Flow old=new Flow(),next=new Flow();flows.put("p",old);
  old.selection.select(old.ticket,old.chooser,true,true);flows.put("p",next);
  check(!old.selection.advance(old.ticket,old.chooser,null,InventoryType.CREATIVE,flows.get("p")==old,true),"old Flow opened");
  check(flows.get("p")==next && next.ticket.matches(next.chooser),"replacement damaged");
  System.out.println("Production selection handoff: survival/creative/default/other menus/stale/ticket/offline/duplicate passed");
 }
}
