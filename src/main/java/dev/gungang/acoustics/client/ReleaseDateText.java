package dev.gungang.acoustics.client;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Display formatting only: configuration and network dates remain ISO dates. */
public final class ReleaseDateText {
    public static String format(String value) {
        if(value==null||value.isBlank())return "";
        try {
            var date=LocalDate.parse(value);
            return date.getYear()+"년 "+date.getMonthValue()+"월 "+date.getDayOfMonth()+"일";
        } catch(DateTimeParseException ignored) {return value;}
    }
    /** A release date still in the future (the weapon or map shows the "추가 예정" tag). */
    public static boolean upcoming(String value) {
        if(value==null||value.isBlank())return false;
        try {return LocalDate.parse(value).isAfter(LocalDate.now());} catch(DateTimeParseException ignored) {return false;}
    }
    public static final String UPCOMING="추가 예정";
    private ReleaseDateText() { }
}
