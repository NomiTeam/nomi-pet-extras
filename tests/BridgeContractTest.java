package team.nomi.pet;

import org.bukkit.entity.Player;

public class BridgeContractTest {

    public static void main(String[] args) throws Exception {
        int index = 0;
        Class<?>[] returns = { boolean.class, String.class, void.class, String.class };
        for (String name : new String[] { "prepare", "apply", "cancel", "getTargetName" }) {
            var m = NomiPetExtras.class.getDeclaredMethod(name, Player.class);
            if (m.getReturnType() != returns[index++]) {
                throw new AssertionError("return type " + name);
            }
            if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                throw new AssertionError(name);
            }
        }
        System.out.println("Public TrMenu bridge signatures passed");
    }
}
