package team.nomi.pet;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import java.lang.reflect.Proxy;

/**
 * API-only bootstrap: InventoryType initializes MenuType registries without a server.
 * Menu entries are unused; block adapters supply air identity for persisted backpack tests. Never packaged.
 */
public final class TestRegistryAccess implements RegistryAccess {

    @SuppressWarnings("unchecked")
    private <T extends Keyed> Registry<T> empty() {
        return (Registry<T>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[] { Registry.class }, (p, m, a) -> {
            if (m.getName().equals("getOrThrow")) {
                return null;
            }
            throw new UnsupportedOperationException("Unexpected registry use: " + m.getName());
        });
    }

    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return empty();
    }

    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        if (key != RegistryKey.BLOCK) {
            return empty();
        }
        return (Registry<T>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[] { Registry.class }, (p, m, a) -> {
            if (m.getName().equals("getOrThrow") || m.getName().equals("get")) {
                String name = a[0].toString();
                return Proxy.newProxyInstance(org.bukkit.block.BlockType.class.getClassLoader(), new Class<?>[] { org.bukkit.block.BlockType.class }, (b, method, args) -> {
                    if (method.getName().equals("isAir")) {
                        return name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air");
                    }
                    throw new UnsupportedOperationException("Unexpected block use: " + method.getName());
                });
            }
            throw new UnsupportedOperationException("Unexpected block registry use: " + m.getName());
        });
    }
}
