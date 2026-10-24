package team.nomi.pet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
/** Terminal confirmation; cancellation remains observable while debit is reentrant. */
final class ResetTransaction {
 enum Result {SUCCESS, USED, PAYMENT_FAILED, PAYMENT_UNKNOWN, REFUNDED, REFUND_FAILED, APPLICATION_UNKNOWN}
 /** NO_CHANGE is allowed only before the mutation boundary (or after verified full restoration). */
 enum Application {APPLIED, NO_CHANGE, UNKNOWN}
 private final AtomicBoolean used=new AtomicBoolean();
 private volatile boolean cancelled;
 void invalidate(){cancelled=true;used.set(true);}
 Result commit(BooleanSupplier debit,Supplier<Application> apply,BooleanSupplier refund,Consumer<RuntimeException> errors){
  if(!used.compareAndSet(false,true))return Result.USED;
  try{if(!debit.getAsBoolean())return Result.PAYMENT_FAILED;}
  catch(RuntimeException e){errors.accept(e);return Result.PAYMENT_UNKNOWN;}
  Application outcome=Application.NO_CHANGE;
  if(!cancelled){
   try{outcome=apply.get();}
   catch(RuntimeException e){errors.accept(e);outcome=Application.UNKNOWN;}
  }
  if(outcome==Application.APPLIED)return Result.SUCCESS;
  if(outcome!=Application.NO_CHANGE)return Result.APPLICATION_UNKNOWN;
  try{return refund.getAsBoolean()?Result.REFUNDED:Result.REFUND_FAILED;}
  catch(RuntimeException e){errors.accept(e);return Result.REFUND_FAILED;}
 }
}
