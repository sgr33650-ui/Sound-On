package com.sgr.volumereveil;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Remet le volume chaque jour à l'heure choisie (sauf week-ends / fériés si demandé). */
public final class VolumeLogic {

    public static final String ACTION_RESTORE = "com.sgr.volumereveil.RESTORE";

    static final String PREFS = "reglages";
    static final String K_ENABLED = "actif";
    static final String K_RING = "volSonnerie";
    static final String K_NOTIF = "volNotif";
    static final String K_HOUR = "heure";
    static final String K_MIN = "minute";
    static final String K_SCHEDULED = "prevuA";
    static final String K_LAST = "derniereRemise";
    static final String K_SKIP_WE = "pasWeekend";
    static final String K_SKIP_FERIE = "pasFeries";
    static final String K_LOG = "journal";

    private VolumeLogic() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void initDefaults(Context c) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        if (!p.contains(K_RING)) {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            e.putBoolean(K_ENABLED, true)
                    .putInt(K_RING, defaultFor(am, AudioManager.STREAM_RING))
                    .putInt(K_NOTIF, defaultFor(am, AudioManager.STREAM_NOTIFICATION));
        }
        if (!p.contains(K_HOUR)) e.putInt(K_HOUR, 7).putInt(K_MIN, 45);
        e.apply();
    }

    private static int defaultFor(AudioManager am, int stream) {
        int cur = am.getStreamVolume(stream);
        int max = am.getStreamMaxVolume(stream);
        return cur > 0 ? cur : Math.max(1, Math.round(max * 0.8f));
    }

    static boolean jourIgnore(Context c, Calendar cal) {
        SharedPreferences p = prefs(c);
        return (p.getBoolean(K_SKIP_WE, true) && JoursOff.estWeekend(cal))
                || (p.getBoolean(K_SKIP_FERIE, true) && JoursOff.estFerie(cal));
    }

    private static PendingIntent restoreIntent(Context c) {
        Intent i = new Intent(c, RestoreReceiver.class).setAction(ACTION_RESTORE);
        return PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Prochaine heure de remise (en sautant les jours ignorés), ou 0 si désactivé. */
    static long nextTime(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(K_ENABLED, true)) return 0;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, p.getInt(K_HOUR, 7));
        cal.set(Calendar.MINUTE, p.getInt(K_MIN, 45));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        for (int i = 0; i < 20 && jourIgnore(c, cal); i++) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = restoreIntent(c);
        am.cancel(pi);
        long when = nextTime(c);
        prefs(c).edit().putLong(K_SCHEDULED, when).apply();
        if (when == 0) return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            // Enregistrée comme une vraie alarme : c'est ce que Xiaomi/Poco bloque le moins.
            Intent show = new Intent(c, MainActivity.class);
            PendingIntent showPi = PendingIntent.getActivity(c, 3, show,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, showPi), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    /** Remet le mode sonnerie et les volumes choisis. Renvoie un message d'état. */
    static String restoreNow(Context c) {
        SharedPreferences p = prefs(c);
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        StringBuilder msg = new StringBuilder();

        try {
            if (am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
        } catch (SecurityException e) {
            msg.append("Ne pas déranger bloque le mode sonnerie. ");
        }

        msg.append(setVol(am, AudioManager.STREAM_RING, p.getInt(K_RING, 5), "sonnerie"));
        msg.append(setVol(am, AudioManager.STREAM_NOTIFICATION, p.getInt(K_NOTIF, 5), "notif"));

        if (nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL) {
            msg.append("(Ne pas déranger encore actif)");
        }
        p.edit().putLong(K_LAST, System.currentTimeMillis()).apply();
        return msg.toString().trim();
    }

    private static String setVol(AudioManager am, int stream, int wanted, String label) {
        int v = Math.min(wanted, am.getStreamMaxVolume(stream));
        try {
            am.setStreamVolume(stream, v, 0);
            return label + " " + am.getStreamVolume(stream) + ". ";
        } catch (SecurityException e) {
            return label + " bloquée (Ne pas déranger). ";
        }
    }

    /** Journal des derniers événements (8 lignes max), visible dans l'appli. */
    static void log(Context c, String line) {
        SharedPreferences p = prefs(c);
        String stamp = new SimpleDateFormat("EEE dd/MM HH:mm", Locale.FRANCE).format(new Date());
        String all = stamp + " — " + line + "\n" + p.getString(K_LOG, "");
        String[] lines = all.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(8, lines.length); i++) {
            if (!lines[i].isEmpty()) sb.append(lines[i]).append("\n");
        }
        p.edit().putString(K_LOG, sb.toString()).apply();
    }
}
