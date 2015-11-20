package org.hogeika.android.atemtally;

import android.app.Application;

/**
 * Created by yuki on 2015/11/11.
 */
public class MyApplication extends Application{
    private ATEMClient atemClient;

    @Override
    public void onCreate() {
        super.onCreate();
        atemClient = new ATEMClient();
    }

    public ATEMClient getATEMClient(){
        return atemClient;
    }

    @Override
    public void onTerminate() {
        atemClient.stop();
        super.onTerminate();
    }
}
