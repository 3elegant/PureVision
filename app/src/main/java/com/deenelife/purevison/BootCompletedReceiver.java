package com.deenelife.purevison;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

// এই ক্লাসটি ফোন রিস্টার্ট হলে কাজ করবে
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        // যদি সিগন্যালটি "BOOT_COMPLETED" হয়
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            // SharedPreferences থেকে সেভ করা ডেটা পড়ি
            SharedPreferences sharedPreferences = context.getSharedPreferences(MainActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

            // চেক করি ইউজার সার্ভিসটি চালু রেখেছিল কিনা
            boolean isServiceEnabled = sharedPreferences.getBoolean(MainActivity.KEY_SERVICE_ENABLED, false);

            if (isServiceEnabled) {
                // যদি সার্ভিস চালু থাকে, তবেই আমরা OverlayService টি চালু করবো
                Intent serviceIntent = new Intent(context, OverlayService.class);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}