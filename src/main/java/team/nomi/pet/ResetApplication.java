package team.nomi.pet;

import de.Keyle.MyPet.api.entity.Pet;
import de.Keyle.MyPet.api.event.PetSelectSkilltreeEvent;
import de.Keyle.MyPet.api.skill.skilltree.Skilltree;

/**
 * REJECTED is possible only before the native mutation call.
 */
final class ResetApplication {

    static String apply(Pet pet, Skilltree target) {
        final double exp;
        final int level;
        try {
            exp = pet.getExp();
            level = pet.getLevel();
        } catch (Throwable error) {
            return "REJECTED";
        }
        try {
            boolean ok = pet.setSkilltree(target, PetSelectSkilltreeEvent.Source.PLAYER_COMMAND);
            return ok && pet.getSkilltree() == target
                    && Double.compare(exp, pet.getExp()) == 0 && level == pet.getLevel()
                    ? "SUCCESS" : "UNCERTAIN";
        } catch (Throwable error) {
            return "UNCERTAIN";
        }
    }
}
