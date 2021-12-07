package com.xc.code;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

         SlowMethodMonitor slowMethodMonitor = new SlowMethodMonitor();
         slowMethodMonitor.start();

        ChoreographerMonitor choreographerMonitor = new  ChoreographerMonitor();
        choreographerMonitor.start();
    }

    public void click(View view) {
        Log.e("TAG","--->");
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 解决，想为什么慢，慢在哪里，跟当时场景
    }
}
