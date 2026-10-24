package team.nomi.pet;
import java.util.concurrent.atomic.AtomicInteger;
public class ReentryTest {
 public static void main(String[] args){
  if(args.length>0 && args[0].equals("unknown")){unknown();return;}
  unknown();
  ResetTransaction tx=new ResetTransaction();AtomicInteger applied=new AtomicInteger(),refund=new AtomicInteger();
  var result=tx.commit(()->{tx.invalidate();return true;},()->{applied.incrementAndGet();return ResetTransaction.Application.APPLIED;},()->{refund.incrementAndGet();return true;},e->{});
  if(!result.name().equals("REFUNDED")||applied.get()!=0||refund.get()!=1)throw new AssertionError("cancelled debit must refund once without apply: "+result+" applied="+applied+" refund="+refund);
  System.out.println("Synchronous invalidation and uncertain application regressions passed");
 }
 static void unknown(){
  AtomicInteger refund=new AtomicInteger();
  var result=new ResetTransaction().commit(()->true,()->{throw new IllegalStateException("already changed pet");},()->{refund.incrementAndGet();return true;},e->{});
  if(!result.name().equals("APPLICATION_UNKNOWN")||refund.get()!=0)throw new AssertionError("uncertain application must require audit without refund: "+result+" refund="+refund);
 }
}
