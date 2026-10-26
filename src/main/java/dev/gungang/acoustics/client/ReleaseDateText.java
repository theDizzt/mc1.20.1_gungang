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
    private ReleaseDateText() { }
}
