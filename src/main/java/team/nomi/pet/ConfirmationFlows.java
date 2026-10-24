package team.nomi.pet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
/** Identity-based ownership, including cleanup after synchronous provider callbacks. */
final class ConfirmationFlows<K,V> {
 private final ConcurrentHashMap<K,V> flows=new ConcurrentHashMap<>();
 private final Consumer<V> invalidate;
 ConfirmationFlows(Consumer<V> invalidate){this.invalidate=invalidate;}
 V get(K key){return flows.get(key);}
 void put(K key,V value){flows.put(key,value);}
 boolean matches(K key,V expected,MenuTicket ticket,Object holder,Object current,boolean online){
  return online && get(key)==expected && ticket.matches(holder) && current==holder;
 }
 int size(){return flows.size();}
 void cancel(K key){V value=flows.remove(key);if(value!=null)invalidate.accept(value);}
 void clear(){flows.values().forEach(invalidate);flows.clear();}
 void finish(K key,V expected,BooleanSupplier sameMenu,Runnable close){
  // Check and remove the exact identity, never a callback's replacement flow.
  java.util.concurrent.atomic.AtomicBoolean removed=new java.util.concurrent.atomic.AtomicBoolean();
  flows.computeIfPresent(key,(k,v)->{if(v==expected){removed.set(true);return null;}return v;});
  if(removed.get()){
   invalidate.accept(expected);
   if(sameMenu.getAsBoolean())close.run();
  }
 }
}
