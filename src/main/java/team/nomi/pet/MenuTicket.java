package team.nomi.pet;
/** A deferred confirmation is valid only while this exact menu remains visible. */
final class MenuTicket {
 private Object holder;
 private boolean valid=true;
 void bind(Object holder){this.holder=holder;}
 boolean matches(Object current){return valid && holder!=null && holder==current;}
 void invalidate(){valid=false;}
}
