package team.nomi.pet;
import de.Keyle.MyPet.api.entity.Pet;
import de.Keyle.MyPet.api.skill.skilltree.Skilltree;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
/** Real MyPet API proxy is a fault-injecting adapter; all transaction/application decisions are production code. */
public class ApplicationTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  for(String mode:new String[]{"normal","false-changed","throw-after-change","wrong-tree","exp-changed","level-changed","post-read-throws"})run(mode);
  for(int mode=0;mode<3;mode++)refund(mode);
  AtomicInteger unknownRefund=new AtomicInteger(),unknownApply=new AtomicInteger(),unknownErrors=new AtomicInteger();
  var unknown=new ResetTransaction().commit(()->{throw new IllegalStateException("debit result unknown");},()->{unknownApply.incrementAndGet();return ResetTransaction.Application.APPLIED;},()->{unknownRefund.incrementAndGet();return true;},e->unknownErrors.incrementAndGet());
  check(unknown==ResetTransaction.Result.PAYMENT_UNKNOWN && unknownRefund.get()==0 && unknownApply.get()==0 && unknownErrors.get()==1,"unknown debit must not apply/refund and must audit");
  System.out.println("Application mutation/false/exception/postconditions and refund failure/exception passed");
 }
 static void run(String mode){
  Skilltree previous=new Skilltree("old"),target=new Skilltree("new");Object[] tree={previous};double[] exp={123.0};int[] level={20};boolean[] changed={false};
  Pet pet=(Pet)Proxy.newProxyInstance(Pet.class.getClassLoader(),new Class<?>[]{Pet.class},(p,m,a)->switch(m.getName()){
   case "getExp"->{if(changed[0]&&mode.equals("post-read-throws"))throw new IllegalStateException("post read");yield exp[0];}
   case "getLevel"->level[0];
   case "getSkilltree"->tree[0];
   case "setSkilltree"->{
    tree[0]=target;changed[0]=true;
    if(mode.equals("throw-after-change"))throw new IllegalStateException("event failed after tree write");
    if(mode.equals("exp-changed"))exp[0]++;
    if(mode.equals("level-changed"))level[0]++;
    if(mode.equals("wrong-tree"))tree[0]=previous;
    yield !mode.equals("false-changed");
   }
   default->throw new UnsupportedOperationException(m.getName());
  });
  AtomicInteger refunds=new AtomicInteger(),errors=new AtomicInteger();
  var result=new ResetTransaction().commit(()->true,()->ResetApplication.apply(pet,target),()->{refunds.incrementAndGet();return true;},e->errors.incrementAndGet());
  check(result==(mode.equals("normal")?ResetTransaction.Result.SUCCESS:ResetTransaction.Result.APPLICATION_UNKNOWN),mode+" result "+result);
  check(refunds.get()==0,mode+" issued free reset");
  check(errors.get()==((mode.equals("throw-after-change")||mode.equals("post-read-throws"))?1:0),mode+" exception audit");
 }
 static void refund(int mode){
  AtomicInteger calls=new AtomicInteger(),errors=new AtomicInteger();
  var result=new ResetTransaction().commit(()->true,()->ResetTransaction.Application.NO_CHANGE,()->{calls.incrementAndGet();if(mode==2)throw new IllegalStateException("refund");return mode==0;},e->errors.incrementAndGet());
  check(result==(mode==0?ResetTransaction.Result.REFUNDED:ResetTransaction.Result.REFUND_FAILED),"refund result");
  check(calls.get()==1&&errors.get()==(mode==2?1:0),"refund count/audit");
 }
}
