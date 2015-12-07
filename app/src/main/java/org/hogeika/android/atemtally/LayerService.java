package org.hogeika.android.atemtally;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

public class LayerService extends Service {
    static public final String EXTRA_REMOTE_ADDR = "remote_addr";
    static public final String EXTRA_CAM_NO = "cam_no";

    MyApplication application;
    ATEMClient client;
    String remote_addr;
    int cam_no = 0;

    View view;
    WindowManager wm;
    AsyncTask task;
    Handler handler;
    Drawable bg_red;
    Drawable bg_green;
    Drawable bg_white;
    Drawable bg_yellow;

    ATEMClient.Callback callback = new ATEMClient.AbstructCallback(){
        @Override
        public void onTallyChange(int pgm, int sby) {
            super.onTallyChange(pgm, sby);
            if (pgm == cam_no){
                changeBG(bg_red);
            }
            else if (sby == cam_no){
                changeBG(bg_green);
            }
            else {
                changeBG(bg_white);
            }
        }

        @Override
        public void onConnect() {
            super.onConnect();
            changeBG(bg_white);
        }

        @Override
        public void onConnectionLoss() {
            super.onConnectionLoss();
            changeBG(bg_yellow);
        }

        @Override
        public void onConnectError(){
            super.onConnectTimeout();
            client.stop();
            stopSelf();
        }

        @Override
        public void onConnectTimeout(){
            super.onConnectTimeout();
            client.stop();
            stopSelf();
        }

        @Override
        public void onError() {
            super.onError();
            client.stop();
            stopSelf();
        }
    };

    private void changeBG(final Drawable bg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                view.setBackground(bg);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            remote_addr = null;
            remote_addr = intent.getStringExtra(EXTRA_REMOTE_ADDR);
            if (remote_addr == null) {
                remote_addr = "192.168.10.240"; // default address
            }
            cam_no = intent.getIntExtra(EXTRA_CAM_NO, 0);
        } else {
            System.out.print("UGH!");
        }

        application = (MyApplication)getApplication();
        client = application.getATEMClient();

        LayoutInflater layoutInflater = LayoutInflater.from(this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);

        view = layoutInflater.inflate(R.layout.overlay, null);

        wm.addView(view, params);

        handler = new Handler(Looper.getMainLooper());
        Resources res = getResources();
        bg_red = res.getDrawable(R.drawable.border_red);
        bg_green = res.getDrawable(R.drawable.border_green);
        bg_white = res.getDrawable(R.drawable.border_white);
        bg_yellow = res.getDrawable(R.drawable.border_yellow);

        changeBG(bg_yellow);

        client.addCallback(callback);
        client.start(remote_addr);
        /*
        task = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                while(!isCancelled()){
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            view.setBackground(bg_green);
                        }
                    });
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            view.setBackground(bg_red);
                        }
                    });
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            view.setBackground(bg_white);
                        }
                    });
                }
                return null;
            }
        };
        task.execute();
        */

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        client.stop();
        client.removeCallback(callback);
//        task.cancel(true);
        wm.removeView(view);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
