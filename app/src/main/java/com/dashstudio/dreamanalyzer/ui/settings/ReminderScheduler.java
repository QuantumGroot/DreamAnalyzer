package com.dashstudio.dreamanalyzer.ui.settings;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class ReminderScheduler {

    private static final String WORK_NAME = "emotion_reminder_work";

    public static void schedule(Context context, String start, String end, boolean enabled) {
        WorkManager wm = WorkManager.getInstance(context);
        wm.cancelUniqueWork(WORK_NAME);

        if (!enabled) {
            return;
        }

        long triggerAt = toNextTriggerTime(start);
        long now = System.currentTimeMillis();
        long delay = Math.max(10_000L, triggerAt - now);

        Constraints constraints = new Constraints.Builder().build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(EmotionReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();

        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req);
    }

    private static long toNextTriggerTime(String hhmm) {
        int hour = 20;
        int minute = 0;
        try {
            String[] parts = hhmm.split(":");
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {
        }

        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        if (c.getTimeInMillis() <= now.getTimeInMillis()) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }
}
