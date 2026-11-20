package com.sgr.volumereveil;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;

/** Toute la logique : programmer la remise du volume après le prochain réveil, et la faire. */
public final class VolumeLogic {

    public static final String ACTION_RESTORE = "com.sgr.volumereveil.RESTORE";

    static final String PREFS = "reglages";
    static final String K_ENABLED = "actif";
    static final String K_RING = "volSonnerie";
    static final String K_NOTIF = "volNotif";
    static final String K_DELAY = "delaiMin";
    static final String K_SCHEDULED = "prevuA";
    static final String K_FOR_ALARM = "pourReveilDe";
    static final String K_LAST = "derniereRemise";
    static final String K_SKIP_WE = "pasWeekend";
    static final String K_SKIP_FERIE = "pasFeries";
    static final String K_SKIPPED = "reveilIgnore";
    public static final String ACTION_RECHECK = "com.sgr.volumereveil.RECHECK";

    /** Vrai si la remise du volume doit être sautée pour un réveil à cette heure-là. */
    static boolean jourIgnore(Context c, long alarmAt) {
        SharedPreferences p = prefs(c);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(alarmAt);
        return (p.getBoolean(K_SKIP_WE, true) && JoursOff.estWeekend(cal))
                || (p.getBoolean(K_SKIP_FERIE, true) && JoursOff.estFerie(cal));
    }

    private static PendingIntent recheckIntent(Context c) {
        Intent i = new Intent(c, RestoreReceiver.class).setAction(ACTION_RECHECK);
        return PendingIntent.getBroadcast(c, 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private VolumeLogic() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Valeurs par défaut au premier lancement : volume actuel s'il n'est pas à zéro, sinon 80 %. */
    static void initDefaults(Context c) {
        SharedPreferences p = prefs(c);
        if (p.contains(K_RING)) return;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        p.edit()
                .putBoolean(K_ENABLED, true)
                .putInt(K_RING, defaultFor(am, AudioManager.STREAM_RING))
                .putInt(K_NOTIF, defaultFor(am, AudioManager.STREAM_NOTIFICATION))
                .putInt(K_DELAY, 2)
                .apply();
    }

    private static int defaultFor(AudioManager am, int stream) {
        int cur = am.getStreamVolume(stream);
        int max = am.getStreamMaxVolume(stream);
        return cur > 0 ? cur : Math.max(1, Math.round(max * 0.8f));
    }

    private static PendingIntent restoreIntent(Context c) {
        Intent i = new Intent(c, RestoreReceiver.class).setAction(ACTION_RESTORE);
        return PendingIntent.getBroadcast(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Programme la remise du volume à (heure du prochain réveil + délai).
     * Si un réveil vient de sonner et que sa remise n'a pas encore eu lieu, on la garde
     * (sinon, en arrêtant le réveil, Android annoncerait déjà celui de demain et on la perdrait).
     */
    static void schedule(Context c) {
        SharedPreferences p = prefs(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();

        long scheduled = p.getLong(K_SCHEDULED, 0);
        long forAlarm = p.getLong(K_FOR_ALARM, 0);
        if (p.getBoolean(K_ENABLED, true) && scheduled > now && forAlarm <= now) {
            return; // remise imminente pour un réveil qui vient de sonner : on n'y touche pas
        }

        am.cancel(restoreIntent(c));
        am.cancel(recheckIntent(c));
        p.edit().remove(K_SCHEDULED).remove(K_FOR_ALARM).remove(K_SKIPPED).apply();
        if (!p.getBoolean(K_ENABLED, true)) return;

        AlarmManager.AlarmClockInfo next = am.getNextAlarmClock();
        if (next == null) return;

        long alarmAt = next.getTriggerTime();
        if (jourIgnore(c, alarmAt)) {
            // Week-end / férié : pas de remise. On revérifie juste après ce réveil
            // pour programmer le suivant.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmAt + 60_000L, recheckIntent(c));
            p.edit().putLong(K_SKIPPED, alarmAt).apply();
            return;
        }
        long when = alarmAt + p.getInt(K_DELAY, 2) * 60_000L;
        PendingIntent pi = restoreIntent(c);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi); // peut avoir quelques minutes de retard
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
        p.edit().putLong(K_SCHEDULED, when).putLong(K_FOR_ALARM, alarmAt).apply();
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
            msg.append("Mode Ne pas déranger actif : autorise l'accès « Ne pas déranger ». ");
        }

        msg.append(setVol(am, AudioManager.STREAM_RING, p.getInt(K_RING, 5), "sonnerie"));
        msg.append(setVol(am, AudioManager.STREAM_NOTIFICATION, p.getInt(K_NOTIF, 5), "notifications"));

        if (!nm.isNotificationPolicyAccessGranted()
                && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL) {
            msg.append("(Ne pas déranger est activé, le son peut rester coupé.)");
        }
        p.edit().putLong(K_LAST, System.currentTimeMillis()).apply();
        return msg.toString().trim();
    }

    private static String setVol(AudioManager am, int stream, int wanted, String label) {
        int v = Math.min(wanted, am.getStreamMaxVolume(stream));
        try {
            am.setStreamVolume(stream, v, 0);
            return "Volume " + label + " remis à " + v + ". ";
        } catch (SecurityException e) {
            return "Impossible de régler le volume " + label + " (Ne pas déranger). ";
        }
    }
}
