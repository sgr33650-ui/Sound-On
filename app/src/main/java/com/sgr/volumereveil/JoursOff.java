package com.sgr.volumereveil;

import java.util.Calendar;

/** Week-ends et jours fériés français (métropole). */
final class JoursOff {
    private JoursOff() {}

    static boolean estWeekend(Calendar c) {
        int d = c.get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY;
    }

    static boolean estFerie(Calendar c) {
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);

        // Fêtes à date fixe
        if ((m == 1 && d == 1) || (m == 5 && d == 1) || (m == 5 && d == 8) || (m == 7 && d == 14)
                || (m == 8 && d == 15) || (m == 11 && d == 1) || (m == 11 && d == 11)
                || (m == 12 && d == 25)) {
            return true;
        }

        // Fêtes mobiles : lundi de Pâques, Ascension, lundi de Pentecôte
        Calendar paques = paques(y);
        int[] decalages = {1, 39, 50};
        for (int off : decalages) {
            Calendar f = (Calendar) paques.clone();
            f.add(Calendar.DAY_OF_YEAR, off);
            if (f.get(Calendar.MONTH) + 1 == m && f.get(Calendar.DAY_OF_MONTH) == d) return true;
        }
        return false;
    }

    /** Dimanche de Pâques (algorithme grégorien de Meeus/Butcher). */
    static Calendar paques(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mois = (h + l - 7 * m + 114) / 31;
        int jour = ((h + l - 7 * m + 114) % 31) + 1;
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, mois - 1, jour);
        return cal;
    }
}
