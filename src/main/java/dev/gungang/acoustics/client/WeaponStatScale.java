package dev.gungang.acoustics.client;
import java.util.List;
import java.util.regex.Pattern;
/** Compares numeric measurements with matching units; textual ratings are not fabricated. */
public final class WeaponStatScale {
    private static final Pattern NUMBER=Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*([\\p{L}%/]*)$");
    private record Value(double number,String unit) { }
    private static Value parse(String text){
        if(text==null)return null;var m=NUMBER.matcher(text.strip());if(!m.matches())return null;
        double n=Double.parseDouble(m.group(1));return Double.isFinite(n)?new Value(n,m.group(2).toLowerCase(java.util.Locale.ROOT)):null;
    }
    public static float fraction(String text,List<String> peers){
        var v=parse(text);if(v==null)return -1;double max=v.number();
        for(String peer:peers){var other=parse(peer);if(other!=null&&other.unit().equals(v.unit()))max=Math.max(max,other.number());}
        return max==0?0:(float)(v.number()/max);
    }
}
