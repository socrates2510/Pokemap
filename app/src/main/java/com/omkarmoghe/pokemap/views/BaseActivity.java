package com.omkarmoghe.pokemap.views;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.omkarmoghe.pokemap.controllers.location.PokemapLocationManager;
import com.omkarmoghe.pokemap.controllers.net.NianticManager;

/**
 * Created by vanshilshah on 19/07/16.
 */
public class BaseActivity extends AppCompatActivity {
    public static final String TAG = "BaseActivity";
    protected PokemapLocationManager.Listener locationListener;
    PokemapLocationManager pokemapLocationManager;
    protected NianticManager nianticManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pokemapLocationManager = PokemapLocationManager.getInstance(this);
        nianticManager = NianticManager.getInstance();

    }

    @Override
    protected void onStart() {
        super.onStart();
        pokemapLocationManager.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        pokemapLocationManager.onStop();
    }

    @Override
    public void onResume(){
        super.onResume();
        pokemapLocationManager.onResume();
        if(locationListener != null){
            pokemapLocationManager.register(locationListener);
        }
    }

    @Override
    public void onPause(){
        PokemapLocationManager.getInstance(this).onPause();
        if(locationListener != null){
            pokemapLocationManager.unregister(locationListener);
        }
        super.onPause();
    }
}
