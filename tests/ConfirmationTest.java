package team.nomi.pet;
import java.util.concurrent.atomic.AtomicInteger;
/** Drives the same registry/ticket/transaction/cleanup used by PetMenus, with synchronous debit callbacks. */
public class ConfirmationTest {
 static final class Flow { final MenuTicket ticket=new MenuTicket();final ResetTransaction tx=new ResetTransaction();final Object holder=new Object();Flow(){ticket.bind(holder);} }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  for(String mode:new String[]{"normal","close","replace","replace-uncancelled","switch","logout","revoke-ticket","stale","offline","ticket"})run(mode);
  System.out.println("Confirmation normal/duplicate/stale/offline/ticket/debit-close/debit-new-flow passed");
 }
 static void run(String mode){
  ConfirmationFlows<String,Flow> flows=new ConfirmationFlows<>(f->{f.ticket.invalidate();f.tx.invalidate();});
  Flow old=new Flow(),next=new Flow();flows.put("p",old);Object[] visible={old.holder};boolean[] online={true};
  AtomicInteger debit=new AtomicInteger(),apply=new AtomicInteger(),refund=new AtomicInteger(),close=new AtomicInteger();
  if(mode.equals("stale"))visible[0]=new Object();
  if(mode.equals("offline"))online[0]=false;
  if(mode.equals("ticket"))old.ticket.invalidate();
  java.util.function.BooleanSupplier active=()->flows.matches("p",old,old.ticket,old.holder,visible[0],online[0]);
  if(!active.getAsBoolean()){check(debit.get()==0,"stale confirmation charged");return;}
  var result=old.tx.commit(()->{
   debit.incrementAndGet();
   if(mode.equals("close")||mode.equals("replace")){flows.cancel("p");visible[0]=new Object();}
   if(mode.startsWith("replace")){flows.put("p",next);visible[0]=next.holder;}
   if(mode.equals("switch"))visible[0]=new Object();
   if(mode.equals("logout"))online[0]=false;
   if(mode.equals("revoke-ticket"))old.ticket.invalidate();
   return true;
  },()->{if(!active.getAsBoolean())return ResetTransaction.Application.NO_CHANGE;apply.incrementAndGet();return ResetTransaction.Application.APPLIED;},()->{refund.incrementAndGet();return true;},e->{throw new AssertionError(e);});
  flows.finish("p",old,()->online[0] && visible[0]==old.holder,close::incrementAndGet);
  boolean normal=mode.equals("normal");
  check(result==(normal?ResetTransaction.Result.SUCCESS:ResetTransaction.Result.REFUNDED),mode+" result "+result);
  check(debit.get()==1&&apply.get()==(normal?1:0)&&refund.get()==(normal?0:1),mode+" counts");
  check(close.get()==(normal||mode.equals("revoke-ticket")?1:0),mode+" closed another menu");
  check(flows.get("p")== (mode.startsWith("replace")?next:null),mode+" lost replacement");
  if(mode.startsWith("replace"))check(next.ticket.matches(next.holder),"new ticket invalidated");
  check(old.tx.commit(()->{throw new AssertionError("double debit");},()->ResetTransaction.Application.APPLIED,()->true,e->{})==ResetTransaction.Result.USED,"duplicate accepted");
 }
}
