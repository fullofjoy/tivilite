package com.tivilite.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.tivilite.tv.MainActivity;

/**
 * 电视开机自启接收器
 * 适合电视盒子、家庭影院与长辈使用，开机无需翻找 App，直达播放
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            boolean autoBoot = context.getSharedPreferences("tivilite_prefs", Context.MODE_PRIVATE)
                    .getBoolean("auto_boot_start", true);
            if (!autoBoot) return;

            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launchIntent);
        }
    }
}
