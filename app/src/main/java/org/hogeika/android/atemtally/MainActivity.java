package org.hogeika.android.atemtally;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;


public class MainActivity extends ActionBarActivity {
    MyApplication application;
    ATEMClient client;

    private EditText et_server;
    private Spinner spin_camera;
    private Button btn_start;
    private Button btn_stop;

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
                }
            });
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

        et_server = (EditText)findViewById(R.id.et_server);
        btn_start = (Button)findViewById(R.id.btn_start);
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String remote_addr = et_server.getText().toString();
                int cam_no = spin_camera.getSelectedItemPosition() + 1;

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
