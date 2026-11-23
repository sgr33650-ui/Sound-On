package com.sgr.volumereveil;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.text.format.DateUtils;

public class RestoreReceiver extends BroadcastReceiver {

    static final String K_BT_DONE = "btFaitLe";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();
        final Context c = context.getApplicationContext();
        final String action = intent.getAction();
        new Thread(() -> {
            try {
                handle(c, action);
            } finally {
                pr.finish();
            }
        }).start();
    }

    private void handle(Context c, String action) {
        VolumeLogic.initDefaults(c);
        SharedPreferences p = VolumeLogic.prefs(c);

        if (VolumeLogic.ACTION_RESTORE.equals(action)) {
            if (p.getBoolean(VolumeLogic.K_ENABLED, true)) {
                VolumeLogic.log(c, "Volume remis : " + VolumeLogic.restoreNow(c));
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            VolumeLogic.log(c, "Téléphone redémarré, remise reprogrammée");
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            bluetoothConnecte(c, p);
            return;
        }
        VolumeLogic.schedule(c);
    }

    /**
     * Appareil Bluetooth connecté APRÈS la remise du matin : on remet aussi son volume,
     * une seule fois par jour.
     */
    private void bluetoothConnecte(Context c, SharedPreferences p) {
        if (!p.getBoolean(VolumeLogic.K_ENABLED, true)
                || !p.getBoolean(VolumeLogic.K_MEDIA_ON, true)
                || !p.getBoolean(VolumeLogic.K_BT_CONNECT, true)) return;
        long last = p.getLong(VolumeLogic.K_LAST, 0);
        if (last == 0 || !DateUtils.isToday(last)) return;          // pas encore de remise aujourd'hui
        if (DateUtils.isToday(p.getLong(K_BT_DONE, 0))) return;      // déjà fait aujourd'hui

        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        // Laisser le temps à la sortie audio Bluetooth de s'activer
        for (int i = 0; i < 10 && !VolumeLogic.bluetoothActif(am); i++) VolumeLogic.pause(1000);
        if (!VolumeLogic.bluetoothActif(am)) return;

        String r = VolumeLogic.setVol(am, AudioManager.STREAM_MUSIC,
                p.getInt(VolumeLogic.K_MEDIA, 8), "média Bluetooth");
        p.edit().putLong(K_BT_DONE, System.currentTimeMillis()).apply();
        VolumeLogic.log(c, "Bluetooth connecté : " + r);
    }
}
