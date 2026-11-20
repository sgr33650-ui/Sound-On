package com.sgr.volumereveil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RestoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        VolumeLogic.initDefaults(context);
        if (VolumeLogic.ACTION_RESTORE.equals(intent.getAction())) {
            if (VolumeLogic.prefs(context).getBoolean(VolumeLogic.K_ENABLED, true)) {
                VolumeLogic.restoreNow(context);
            }
            VolumeLogic.prefs(context).edit()
                    .remove(VolumeLogic.K_SCHEDULED).remove(VolumeLogic.K_FOR_ALARM).apply();
        }
        // Dans tous les cas (démarrage, réveil modifié, remise faite) : programmer la suivante.
        VolumeLogic.schedule(context);
    }
}
