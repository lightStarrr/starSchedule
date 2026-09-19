package com.suda.yzune.wakeupschedule;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String SOURCE_PACKAGE = "com.star.schedule";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WakeUpProxyProvider.notifySystem(this);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(SOURCE_PACKAGE);
        if (launchIntent == null) {
            Toast.makeText(this, R.string.source_app_missing, Toast.LENGTH_LONG).show();
        } else {
            startActivity(launchIntent);
        }
        finish();
    }
}
