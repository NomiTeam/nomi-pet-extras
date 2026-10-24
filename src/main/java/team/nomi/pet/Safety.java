package team.nomi.pet;
final class Safety {
 static boolean allowed(boolean owner,boolean active,boolean unchanged,boolean permission,boolean items,int required,boolean inherited,boolean type,boolean requirements){
  return owner && active && unchanged && permission && !items && required<=1 && !inherited && type && requirements;
 }
 static <T> boolean hasItems(T[] contents,java.util.function.Predicate<T> occupied){return java.util.Arrays.stream(contents).anyMatch(i->i!=null && occupied.test(i));}
 static double cost(double d){return Double.isFinite(d)&&d>=0?d:500;}
}
