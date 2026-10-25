package team.nomi.pet;

public class SafetyTest {

    public static void main(String[] args) {
        if (!Safety.allowed(true, true, true, true, false, 1, false, true, true)) {
            throw new AssertionError("valid");
        }
        for (int n = 0; n < 9; n++) {
            boolean[] b = { true, true, true, true, false, false, true, true };
            if (n < 4) {
                b[n] = false;
            } else if (n == 4) {
                b[4] = true;
            } else if (n == 6) {
                b[5] = true;
            } else if (n == 7) {
                b[6] = false;
            } else if (n == 8) {
                b[7] = false;
            }
            if (Safety.allowed(b[0], b[1], b[2], b[3], b[4], n == 5 ? 2 : 1, b[5], b[6], b[7])) {
                throw new AssertionError("gate " + n);
            }
        }
        String[] slots = new String[54];
        slots[53] = "hidden";
        if (!Safety.hasItems(slots, x -> !x.isEmpty())) {
            throw new AssertionError("hidden persisted slot");
        }
        slots[53] = null;
        if (Safety.hasItems(slots, x -> !x.isEmpty())) {
            throw new AssertionError("empty");
        }
        System.out.println("Owner/current pet/previous/permission/backpack/level/inheritance/type/native gates passed");
    }
}
