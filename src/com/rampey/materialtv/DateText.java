package com.rampey.materialtv;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The date line under the clock and in the screensaver, written the way the TV's language writes it. */
final class DateText {
    private DateText() {
    }

    static String format(Date date, Locale locale) {
        // Vietnamese: "Thứ Hai, Ngày 26 Tháng 10 năm 2026" (the platform pattern would give "Thứ Hai, tháng 10 26").
        String pattern = "vi".equals(locale.getLanguage()) ? "EEEE, 'Ngày' d 'Tháng' M 'năm' yyyy" : "EEEE, MMMM d";
        return new SimpleDateFormat(pattern, locale).format(date);
    }
}
