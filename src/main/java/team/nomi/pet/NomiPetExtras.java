package team.nomi.pet;

import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

public final class NomiPetExtras extends JavaPlugin {
 private PetMenus menus;
 @Override public void onEnable(){
  saveDefaultConfig();
  try{
   var registration=getServer().getServicesManager().getRegistration(Economy.class);
   if(registration==null){getLogger().severe("No Vault economy provider; NomiPetExtras disabled.");getServer().getPluginManager().disablePlugin(this);return;}
   menus=new PetMenus(this,registration.getProvider());
   menus.register();getServer().getPluginManager().registerEvents(menus,this);getCommand("nomipetreset").setExecutor(menus);
  }catch(LinkageError|RuntimeException e){getLogger().log(java.util.logging.Level.SEVERE,"Required MyPet 4.0.4 GUI/API unavailable; disabling",e);getServer().getPluginManager().disablePlugin(this);}
 }
 @Override public void onDisable(){if(menus!=null)menus.shutdown();}
}
