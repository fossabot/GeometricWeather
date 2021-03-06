package wangdaye.com.geometricweather.service.polling;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import java.util.Calendar;

import wangdaye.com.geometricweather.GeometricWeather;
import wangdaye.com.geometricweather.R;
import wangdaye.com.geometricweather.service.FakeForegroundService;

/**
 * Polling service.
 * */

public class PollingService extends Service {

    private static TimeTickReceiver receiver;

    private static float pollingRate;
    private static long lastUpdateNormalViewTime;
    private static String todayForecastTime;
    private static String tomorrowForecastTime;

    public static final String KEY_UPDATE_SETTINGS = "update_settings";
    public static final String KEY_UPDATE_RESULT = "update_result";
    public static final String KEY_POLLING_RATE = "polling_rate";
    public static final String KEY_TODAY_FORECAST_TIME = "today_forecast_time";
    public static final String KEY_TOMORROW_FORECAST_TIME = "tomorrow_forecast_time";

    private class TimeTickReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!TextUtils.isEmpty(action)) {
                switch (intent.getAction()) {
                    case Intent.ACTION_TIME_TICK:
                        doRefreshWork();
                        break;

                    case Intent.ACTION_TIME_CHANGED:
                    case Intent.ACTION_TIMEZONE_CHANGED:
                        lastUpdateNormalViewTime = -1;
                        doRefreshWork();
                        break;
                }
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground();
        initData();
        registerReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        readData(intent);
        doRefreshWork();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver();
        stopForeground();
    }

    private void initData() {
        pollingRate = 1.5F;
        todayForecastTime = GeometricWeather.DEFAULT_TODAY_FORECAST_TIME;
        tomorrowForecastTime = GeometricWeather.DEFAULT_TOMORROW_FORECAST_TIME;
        lastUpdateNormalViewTime = System.currentTimeMillis();
    }

    private void readData(Intent intent) {
        if (intent != null) {
            if (intent.getBooleanExtra(KEY_UPDATE_SETTINGS, false)) {
                pollingRate = intent.getFloatExtra(KEY_POLLING_RATE, 1.5f);
                todayForecastTime = intent.getStringExtra(KEY_TODAY_FORECAST_TIME);
                tomorrowForecastTime = intent.getStringExtra(KEY_TOMORROW_FORECAST_TIME);
            }
            if (!intent.getBooleanExtra(KEY_UPDATE_RESULT, true)) {
                lastUpdateNormalViewTime = System.currentTimeMillis() - getPollingInterval() + 15 * 60 * 1000;
            }
        }
    }

    private void doRefreshWork() {
        if (lastUpdateNormalViewTime < 0
                || System.currentTimeMillis() - lastUpdateNormalViewTime > getPollingInterval()) {
            lastUpdateNormalViewTime = System.currentTimeMillis();
            Intent intent = new Intent(this, PollingNormalUpdateService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        if (!TextUtils.isEmpty(todayForecastTime) && isForecastTime(todayForecastTime)) {
            Intent intent = new Intent(this, PollingTodayForecastUpdateService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        if (!TextUtils.isEmpty(tomorrowForecastTime) && isForecastTime(tomorrowForecastTime)) {
            Intent intent = new Intent(this, PollingTomorrowForecastUpdateService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        receiver = new TimeTickReceiver();
        registerReceiver(receiver, filter);
    }

    private void unregisterReceiver() {
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    private void startForeground() {
        NotificationManager manager = ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    GeometricWeather.NOTIFICATION_CHANNEL_ID_BACKGROUND,
                    GeometricWeather.getNotificationChannelName(this, GeometricWeather.NOTIFICATION_CHANNEL_ID_BACKGROUND),
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setLightColor(ContextCompat.getColor(this, R.color.colorPrimary));
            manager.createNotificationChannel(channel);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(
                    GeometricWeather.NOTIFICATION_ID_RUNNING_IN_BACKGROUND,
                    getForecastNotification(this, true));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startForeground(
                    GeometricWeather.NOTIFICATION_ID_RUNNING_IN_BACKGROUND,
                    getForecastNotification(this, false));
            startService(new Intent(this, FakeForegroundService.class));
        } else {
            startForeground(
                    GeometricWeather.NOTIFICATION_ID_RUNNING_IN_BACKGROUND,
                    getForecastNotification(this, true));
            startService(new Intent(this, FakeForegroundService.class));
        }
    }

    private void stopForeground() {
        stopForeground(true);
    }

    public static Notification getForecastNotification(Context context, boolean setIcon) {
        return new NotificationCompat.Builder(context, GeometricWeather.NOTIFICATION_CHANNEL_ID_BACKGROUND)
                .setSmallIcon(setIcon ? R.drawable.ic_running_in_background : 0)
                .setContentTitle(context.getString(R.string.geometric_weather))
                .setContentText(context.getString(R.string.feedback_running_in_background))
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .setAutoCancel(true)
                .build();
    }

    private long getPollingInterval() {
        return (long) (pollingRate * 1000 * 60 * 60);
    }

    private static boolean isForecastTime(String time) {
        int realTimes[] = new int[] {
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                Calendar.getInstance().get(Calendar.MINUTE)};
        int setTimes[] = new int[]{
                Integer.parseInt(time.split(":")[0]),
                Integer.parseInt(time.split(":")[1])};
        return realTimes[0] == setTimes[0] && realTimes[1] == setTimes[1];
    }
}