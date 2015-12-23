package org.hogeika.android.atemtally;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity {
    MyApplication application;
    ATEMClient client;

    private View view_bg;
    private EditText et_server;
    private Spinner spin_camera;
    private Button btn_start;
    private Button btn_stop;

    private int cam_no;

    ATEMClient.Callback callback = new ATEMClient.AbstructCallback(){
        @Override
        public void onStart() {
            super.onStart();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btn_start.setEnabled(false);
                    btn_stop.setEnabled(true);
                    spin_camera.setEnabled(false);
                    // Keep screen on
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @Override
        public void onStop() {
            super.onStop();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btn_start.setEnabled(true);
                    btn_stop.setEnabled(false);
                    spin_camera.setEnabled(true);
                    // Keep screen off
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
            changeBG(Color.TRANSPARENT);
        }

        private void changeBG(final int color){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    view_bg.setBackgroundColor(color);
                }
            });
        }
        @Override
        public void onTallyChange(int pgm, int sby) {
            super.onTallyChange(pgm, sby);
            if (pgm == cam_no){
                changeBG(Color.RED);
            }
            else if (sby == cam_no){
                changeBG(Color.GREEN);
            }
            else {
                changeBG(Color.TRANSPARENT);
            }
        }

        @Override
        public void onConnectionLoss() {
            super.onConnectionLoss();
            changeBG(Color.YELLOW);
        }

        @Override
        public void onConnect() {
            super.onConnect();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Connected Now", Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public void onConnectTimeout() {
            super.onConnectTimeout();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Connect Timeout", Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public void onError() {
            super.onError();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Connection Timeout", Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        application = (MyApplication)getApplication();
        client = application.getATEMClient();

        view_bg = findViewById(R.id.view_bg);
        et_server = (EditText)findViewById(R.id.et_server);
        btn_start = (Button)findViewById(R.id.btn_start);
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String remote_addr = et_server.getText().toString();
                cam_no = spin_camera.getSelectedItemPosition() + 1;

                Intent intent = new Intent(MainActivity.this, LayerService.class);
                intent.putExtra(LayerService.EXTRA_REMOTE_ADDR, remote_addr);
                intent.putExtra(LayerService.EXTRA_CAM_NO, cam_no);
                startService(intent);
            }
        });
        btn_stop = (Button)findViewById(R.id.btn_stop);
        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, LayerService.class));
            }
        });

        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item);
        adapter.add("Cam 1");
        adapter.add("Cam 2");
        adapter.add("Cam 3");
        adapter.add("Cam 4");
        adapter.add("Cam 5");
        adapter.add("Cam 6");
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin_camera = (Spinner)findViewById(R.id.spin_camera);
        spin_camera.setAdapter(adapter);

        if (client.isRunning()){
            btn_start.setEnabled(false);
            btn_stop.setEnabled(true);
            spin_camera.setEnabled(false);
        } else {
            btn_start.setEnabled(true);
            btn_stop.setEnabled(false);
            spin_camera.setEnabled(true);
        }

        client.addCallback(callback);
    }

    @Override
    protected void onDestroy() {
        client.removeCallback(callback);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
