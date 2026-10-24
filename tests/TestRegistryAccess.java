package team.nomi.pet;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import java.lang.reflect.Proxy;

/** API-only bootstrap: InventoryType initializes MenuType registries without a server.
 * No menu registry entries are used by the handoff tests. Never packaged in the plugin. */
public final class TestRegistryAccess implements RegistryAccess {
 @SuppressWarnings("unchecked") private <T extends Keyed> Registry<T> empty(){
  return (Registry<T>)Proxy.newProxyInstance(Registry.class.getClassLoader(),new Class<?>[]{Registry.class},
   (p,m,a)->{if(m.getName().equals("getOrThrow"))return null;
    throw new UnsupportedOperationException("Unexpected registry use: "+m.getName());});
 }
 public <T extends Keyed> Registry<T> getRegistry(Class<T> type){return empty();}
 public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key){return empty();}
}
