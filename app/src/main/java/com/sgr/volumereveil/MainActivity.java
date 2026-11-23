package com.sgr.volumereveil;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private AudioManager audio;
    private TextView status;
    private Button btnExact, btnDnd, btnTime;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        VolumeLogic.initDefaults(this);
        prefs = VolumeLogic.prefs(this);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView intro = text("Chaque matin à l'heure choisie, l'appli remet le volume "
                + "de la sonnerie et des notifications aux niveaux ci-dessous.", 15);
        root.addView(intro);

        Switch sw = new Switch(this);
        sw.setText("Activé");
        sw.setTextSize(18);
        sw.setChecked(prefs.getBoolean(VolumeLogic.K_ENABLED, true));
        sw.setPadding(0, dp(16), 0, dp(8));
        sw.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean(VolumeLogic.K_ENABLED, on).apply();
            refresh();
        });
        root.addView(sw);

        btnTime = new Button(this);
        btnTime.setTextSize(18);
        btnTime.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> {
            prefs.edit().putInt(VolumeLogic.K_HOUR, h).putInt(VolumeLogic.K_MIN, m).apply();
            refresh();
        }, prefs.getInt(VolumeLogic.K_HOUR, 7), prefs.getInt(VolumeLogic.K_MIN, 45), true).show());
        root.addView(btnTime);

        root.addView(checkbox("Pas le week-end (samedi, dimanche)", VolumeLogic.K_SKIP_WE));
        root.addView(checkbox("Pas les jours fériés", VolumeLogic.K_SKIP_FERIE));

        addSlider("Volume sonnerie", VolumeLogic.K_RING,
                audio.getStreamMaxVolume(AudioManager.STREAM_RING), 0, "");
        addSlider("Volume notifications", VolumeLogic.K_NOTIF,
                audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0, "");

        status = text("", 15);
        status.setPadding(0, dp(16), 0, dp(8));
        root.addView(status);

        Button test = new Button(this);
        test.setText("Remettre le volume maintenant (test)");
        test.setOnClickListener(v -> {
            String r = VolumeLogic.restoreNow(this);
            VolumeLogic.log(this, "Test manuel : " + r);
            Toast.makeText(this, r, Toast.LENGTH_LONG).show();
            refresh();
        });
        root.addView(test);

        btnExact = new Button(this);
        btnExact.setText("Autoriser l'heure précise (alarmes exactes)");
        btnExact.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        root.addView(btnExact);

        btnDnd = new Button(this);
        btnDnd.setText("Autoriser l'accès « Ne pas déranger »");
        btnDnd.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        root.addView(btnDnd);

        Button btnApp = new Button(this);
        btnApp.setText("Réglages de l'appli (démarrage auto, batterie)");
        btnApp.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        root.addView(btnApp);

        TextView tip = text("Sur Poco / Xiaomi : dans les réglages de l'appli, active "
                + "« Démarrage automatique » et mets « Économiseur de batterie » sur "
                + "« Aucune restriction », sinon le système peut bloquer la remise du volume.", 13);
        tip.setPadding(0, dp(12), 0, 0);
        root.addView(tip);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void addSlider(String label, String key, int max, int min, String unit) {
        TextView t = text("", 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, 0);
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        int val = Math.min(prefs.getInt(key, min), max);
        s.setProgress(val);
        t.setText(label + " : " + val + unit + (unit.isEmpty() ? " / " + max : ""));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean user) {
                int v = Math.max(min, p);
                t.setText(label + " : " + v + unit + (unit.isEmpty() ? " / " + max : ""));
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                prefs.edit().putInt(key, Math.max(min, sb.getProgress())).apply();
                refresh();
            }
        });
        root.addView(t);
        root.addView(s);
    }

    private void refresh() {
        // Re-programmer avec les réglages actuels
        VolumeLogic.schedule(this);

        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        btnTime.setText(String.format(Locale.FRANCE, "Heure de remise du volume : %02d:%02d",
                prefs.getInt(VolumeLogic.K_HOUR, 7), prefs.getInt(VolumeLogic.K_MIN, 45)));

        DateFormat f = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, Locale.FRANCE);
        StringBuilder sb = new StringBuilder();
        long sched = prefs.getLong(VolumeLogic.K_SCHEDULED, 0);
        sb.append("Prochaine remise du volume : ")
                .append(sched == 0 ? "désactivée" : f.format(new Date(sched)));
        long last = prefs.getLong(VolumeLogic.K_LAST, 0);
        sb.append("\nDernière remise : ").append(last == 0 ? "jamais" : f.format(new Date(last)));
        String journal = prefs.getString(VolumeLogic.K_LOG, "");
        if (!journal.isEmpty()) sb.append("\n\nJournal :\n").append(journal.trim());
        status.setText(sb.toString());

        boolean exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        btnExact.setVisibility(exactOk ? View.GONE : View.VISIBLE);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        btnDnd.setVisibility(nm.isNotificationPolicyAccessGranted() ? View.GONE : View.VISIBLE);
    }

    private CheckBox checkbox(String label, String key) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextSize(16);
        cb.setChecked(prefs.getBoolean(key, true));
        cb.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean(key, on).apply();
            refresh();
        });
        return cb;
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
