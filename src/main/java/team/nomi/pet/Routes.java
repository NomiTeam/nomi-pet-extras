package team.nomi.pet;
import de.Keyle.MyPet.api.entity.PetType;
import de.Keyle.MyPet.api.skill.skilltree.Skilltree;
final class Routes {
 static boolean base(int level,PetType type,Skilltree tree){return tree!=null && tree.getRequiredLevel()<=1 && level>=tree.getRequiredLevel() && (tree.getInheritedSkilltreeName()==null || tree.getInheritedSkilltreeName().isBlank())
  && (tree.getMobTypes().isEmpty() || tree.getMobTypes().contains(type));}
}
