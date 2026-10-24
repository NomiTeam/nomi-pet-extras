package team.nomi.pet;
import de.Keyle.MyPet.api.entity.PetType;
import de.Keyle.MyPet.api.skill.skilltree.Skilltree;
import java.util.List;
public class RoutesTest {
 public static void main(String[] args){
  PetType wolf=PetType.register("TestWolf",de.Keyle.MyPet.api.entity.Pet.class);
  PetType cat=PetType.register("TestCat",de.Keyle.MyPet.api.entity.Pet.class);
  Skilltree base=new Skilltree("base");base.setRequiredLevel(1);
  check(Routes.base(20,wolf,base));
  check(!Routes.base(0,wolf,base));
  base.setRequiredLevel(2);check(!Routes.base(20,wolf,base));
  base.setRequiredLevel(1);base.setInheritedSkilltreeName("parent");check(!Routes.base(20,wolf,base));
  base.setInheritedSkilltreeName(null);base.setMobTypes(List.of(cat));check(!Routes.base(20,wolf,base));
  check(Routes.base(20,cat,base));
  System.out.println("Real MyPet Skilltree route assertions passed");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
}
