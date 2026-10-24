package team.nomi.pet;
import java.util.concurrent.atomic.AtomicInteger;
public class ResetTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){verify();System.out.println("Reset payment, refund and safety assertions passed");}
 static void verify(){
  AtomicInteger debit=new AtomicInteger(),apply=new AtomicInteger(),refund=new AtomicInteger();
  var tx=new ResetTransaction();
  check(tx.commit(()->{debit.incrementAndGet();return true;},()->{apply.incrementAndGet();return ResetTransaction.Application.APPLIED;},()->{refund.incrementAndGet();return true;},e->{})==ResetTransaction.Result.SUCCESS);
  check(tx.commit(()->true,()->ResetTransaction.Application.APPLIED,()->true,e->{})==ResetTransaction.Result.USED);
  check(debit.get()==1&&apply.get()==1&&refund.get()==0);
  check(new ResetTransaction().commit(()->false,()->{throw new AssertionError();},()->true,e->{})==ResetTransaction.Result.PAYMENT_FAILED);
  check(new ResetTransaction().commit(()->true,()->ResetTransaction.Application.NO_CHANGE,()->{refund.incrementAndGet();return true;},e->{})==ResetTransaction.Result.REFUNDED);
  check(refund.get()==1);
  check(new ResetTransaction().commit(()->true,()->{throw new IllegalStateException("apply");},()->false,e->{})==ResetTransaction.Result.APPLICATION_UNKNOWN);
  check(new ResetTransaction().commit(()->true,()->ResetTransaction.Application.NO_CHANGE,()->{throw new IllegalStateException("refund");},e->{})==ResetTransaction.Result.REFUND_FAILED);
  check(new ResetTransaction().commit(()->{throw new IllegalStateException("unknown payment");},()->ResetTransaction.Application.APPLIED,()->true,e->{})==ResetTransaction.Result.PAYMENT_UNKNOWN);
  check(Safety.allowed(true,true,true,true,false,1,false,true,true));
  for(int n=0;n<9;n++){
   boolean[] b={true,true,true,true,false,false,true,true};
   if(n<4)b[n]=false;else if(n==4)b[4]=true;else if(n==6)b[5]=true;else if(n==7)b[6]=false;else if(n==8)b[7]=false;
   check(!Safety.allowed(b[0],b[1],b[2],b[3],b[4],n==5?2:1,b[5],b[6],b[7]));
  }
  MenuTicket ticket=new MenuTicket();Object old=new Object(),other=new Object();
  ticket.bind(old);check(ticket.matches(old));check(!ticket.matches(other));
  ticket.invalidate();check(!ticket.matches(old));ticket.bind(other);check(!ticket.matches(other));
  var failed=new ResetTransaction();
  check(failed.commit(()->false,()->ResetTransaction.Application.APPLIED,()->true,e->{})==ResetTransaction.Result.PAYMENT_FAILED);
  check(failed.commit(()->{throw new AssertionError("must never debit twice");},()->ResetTransaction.Application.APPLIED,()->true,e->{})==ResetTransaction.Result.USED);
  var cancelled=new ResetTransaction();cancelled.invalidate();
  check(cancelled.commit(()->{throw new AssertionError("cancel charged");},()->ResetTransaction.Application.APPLIED,()->true,e->{})==ResetTransaction.Result.USED);
  String[] storage=new String[54];storage[53]="hidden item";
  check(Safety.hasItems(storage,x->!x.isEmpty()));
  storage[53]=null;check(!Safety.hasItems(storage,x->!x.isEmpty()));
  storage[0]="";check(!Safety.hasItems(storage,x->!x.isEmpty()));
  check(Safety.cost(Double.NaN)==500 && Safety.cost(-1)==500 && Safety.cost(0)==0);
 }
}
