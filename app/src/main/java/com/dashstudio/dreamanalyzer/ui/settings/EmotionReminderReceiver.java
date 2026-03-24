package com.dashstudio.dreamanalyzer.ui.settings;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.dashstudio.dreamanalyzer.MainActivity;
import com.dashstudio.dreamanalyzer.R;
import com.dashstudio.dreamanalyzer.data.LocalDataRepository;

public class EmotionReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "emotion_reminder_channel";
    public static final int NOTIFY_ID = 10086;

    @Override
    public void onReceive(Context context, Intent intent) {
        LocalDataRepository repository = new LocalDataRepository(context);
        if (!repository.isNotificationEnabled()) {
            return;
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("睡眠情绪提醒")
                .setContentText("记得查看昨晚与近期睡眠的情绪状态")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        nm.notify(NOTIFY_ID, builder.build());
    }
}
