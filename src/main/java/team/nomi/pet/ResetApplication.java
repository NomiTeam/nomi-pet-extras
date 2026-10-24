package team.nomi.pet;
import de.Keyle.MyPet.api.entity.Pet;
import de.Keyle.MyPet.api.event.PetSelectSkilltreeEvent;
import de.Keyle.MyPet.api.skill.skilltree.Skilltree;
/** Once MyPet is entered, false/exception cannot prove absence of skill/event side effects. */
final class ResetApplication {
 static ResetTransaction.Application apply(Pet pet,Skilltree target){
  double exp=pet.getExp();int level=pet.getLevel();
  boolean ok=pet.setSkilltree(target,PetSelectSkilltreeEvent.Source.PLAYER_COMMAND);
  return ok && pet.getSkilltree()==target && Double.compare(exp,pet.getExp())==0 && level==pet.getLevel()
   ?ResetTransaction.Application.APPLIED:ResetTransaction.Application.UNKNOWN;
 }
}
