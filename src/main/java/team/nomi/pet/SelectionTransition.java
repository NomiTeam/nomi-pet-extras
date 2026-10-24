package team.nomi.pet;

import org.bukkit.event.inventory.InventoryType;

/** Single-use handoff from the native chooser to our confirmation. */
final class SelectionTransition {
 private boolean selected,opened;
 boolean select(MenuTicket ticket,Object current,boolean live,boolean online){
  if(selected || !live || !online || !ticket.matches(current))return false;
  selected=true;return true;
 }
 boolean advance(MenuTicket ticket,Object chooser,Object current,InventoryType type,boolean live,boolean online){
  if(!selected || opened || !live || !online || !ticket.matches(chooser) || current==chooser
    || (type!=InventoryType.CRAFTING && type!=InventoryType.CREATIVE))return false;
  opened=true;return true;
 }
}
