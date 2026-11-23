package com.sgr.volumereveil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RestoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        VolumeLogic.initDefaults(context);
        String action = intent.getAction();
        if (VolumeLogic.ACTION_RESTORE.equals(action)) {
            if (VolumeLogic.prefs(context).getBoolean(VolumeLogic.K_ENABLED, true)) {
                VolumeLogic.log(context, "Volume remis : " + VolumeLogic.restoreNow(context));
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            VolumeLogic.log(context, "Téléphone redémarré, remise reprogrammée");
        }
        VolumeLogic.schedule(context);
    }
}
