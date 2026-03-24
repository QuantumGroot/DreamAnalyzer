package com.dashstudio.dreamanalyzer.ui.settings;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.dashstudio.dreamanalyzer.MainActivity;
import com.dashstudio.dreamanalyzer.R;
import com.dashstudio.dreamanalyzer.data.LocalDataRepository;

public class EmotionReminderWorker extends Worker {

    public EmotionReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        LocalDataRepository repository = new LocalDataRepository(context);
        if (!repository.isNotificationEnabled()) {
            return Result.success();
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return Result.retry();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    EmotionReminderReceiver.CHANNEL_ID,
                    "睡眠情绪提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("提醒查看昨晚与近期睡眠的情绪状态");
            nm.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(context, 0, openIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, EmotionReminderReceiver.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("睡眠情绪提醒")
                .setContentText("记得查看昨晚与近期睡眠的情绪状态")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        nm.notify(EmotionReminderReceiver.NOTIFY_ID, builder.build());

        // 发出通知后，重新安排下一次
        ReminderScheduler.schedule(
                context,
                repository.getNotificationStart(),
                repository.getNotificationEnd(),
                repository.isNotificationEnabled()
        );

        return Result.success();
    }
}
