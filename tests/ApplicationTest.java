package team.nomi.pet;

import de.Keyle.MyPet.api.entity.Pet;
import de.Keyle.MyPet.api.skill.skilltree.Skilltree;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real MyPet API proxy is a fault-injecting adapter; all transaction/application decisions are production code.
 */
public class ApplicationTest {

    static void check(boolean b, String s) {
        if (!b) {
            throw new AssertionError(s);
        }
    }

    public static void main(String[] args) {
        for (String mode : new String[] { "normal",
                "pre-read-throws",
                "false-changed",
                "throw-after-change",
                "error-after-change",
                "wrong-tree",
                "exp-changed",
                "level-changed",
                "post-read-throws" }) {
            run(mode);
        }
        System.out.println("Native application mutation/false/exception/postconditions passed");
    }

    static void run(String mode) {
        Skilltree previous = new Skilltree("old"), target = new Skilltree("new");
        Object[] tree = { previous };
        double[] exp = { 123.0 };
        int[] level = { 20 };
        boolean[] changed = { false };
        Pet pet = (Pet) Proxy.newProxyInstance(Pet.class.getClassLoader(), new Class<?>[] { Pet.class }, (p, m, a) -> switch (m.getName()) {
            case "getExp" -> {
                if (mode.equals("pre-read-throws")) {
                    throw new IllegalStateException("pre read");
                }
                if (changed[0] && mode.equals("post-read-throws")) {
                    throw new IllegalStateException("post read");
                }
                yield exp[0];
            }
            case "getLevel" -> level[0];
            case "getSkilltree" -> tree[0];
            case "setSkilltree" -> {
                check(a[1] == de.Keyle.MyPet.api.event.PetSelectSkilltreeEvent.Source.PLAYER_COMMAND, "native source");
                tree[0] = target;
                changed[0] = true;
                if (mode.equals("error-after-change")) {
                    throw new AssertionError("provider error after mutation");
                }
                if (mode.equals("throw-after-change")) {
                    throw new IllegalStateException("event failed after tree write");
                }
                if (mode.equals("exp-changed")) {
                    exp[0]++;
                }
                if (mode.equals("level-changed")) {
                    level[0]++;
                }
                if (mode.equals("wrong-tree")) {
                    tree[0] = previous;
                }
                yield !mode.equals("false-changed");
            }
            default -> throw new UnsupportedOperationException(m.getName());
        });
        String result = ResetApplication.apply(pet, target);
        check(result.equals(mode.equals("normal") ? "SUCCESS" : mode.equals("pre-read-throws") ? "REJECTED" : "UNCERTAIN"), mode + " result " + result);
        check(changed[0] != mode.equals("pre-read-throws"), "mutation boundary " + mode);
    }
}
