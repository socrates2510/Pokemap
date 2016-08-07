package com.omkarmoghe.pokemap.controllers.location;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.Location;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.maps.model.LatLng;
import com.omkarmoghe.pokemap.controllers.service.ContextService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vanshilshah on 19/07/16.
 */
@SuppressWarnings("deprecation")
public class PokemapLocationManager extends ContextService {
    private static final String TAG = "PokemapLocationManager";
    private static PokemapLocationManager instance;

    private GpsProvider gpsProvider;
    private ServiceStatus gpsStatus;
    private FusedLocationProvider locationProvider;
    private ServiceStatus locationStatus;

    private List<Listener> listeners;
    private Location location;
    private GpsSatellite satellite;

    public static PokemapLocationManager getInstance(Context context) {
        if (instance == null) {
            instance = new PokemapLocationManager(context);
        }

        return instance;
    }

    private PokemapLocationManager(final Context context) {
        super(context);
        gpsProvider = new GpsProvider(context, 10000, 0.0f);
        gpsStatus = ServiceStatus.UNDEFINED;
        gpsProvider.setListener(new GpsProvider.GpsProviderListener() {
            public void onGpsStatusUpdate(int timeToFix, GpsSatellite[] satellites) {
                if(satellites != null && satellites.length > 0) {
                    satellite = satellites[0];
                }
            }

            public void onProviderLocation(Location location) {
                //Do nothing
            }

            public void onProviderStatus(ServiceStatus status) {
                gpsStatus = status;
            }
        });

        locationProvider = new FusedLocationProvider(context);
        locationStatus = ServiceStatus.UNDEFINED;
        locationProvider.setListener(new Provider.ProviderListener() {
            @Override
            public void onProviderLocation(Location location) {
                PokemapLocationManager.this.location = location;
                notifyLocationChanged(location);
            }

            @Override
            public void onProviderStatus(ServiceStatus serviceStatus) {
                locationStatus = serviceStatus;
            }
        });

        listeners = new ArrayList<>();
    }

    public LatLng getLatLng(){
        //Don't getLatitude without checking if location is not null... it will throw sys err...
        if(location != null){
            return new LatLng(location.getLatitude(), location.getLongitude());
        } else {
            notifyLocationFetchFailed(null);
        }
        return null;
    }

    public Location getLocation(){
        return location;
    }

    public GpsSatellite getSatellite(){
        return satellite;
    }

    public void onStart(){
        gpsProvider.onStart();
        locationProvider.onStart();
    }

    public void onStop(){
        gpsProvider.onStop();
        locationProvider.onStop();
    }

    public void onResume(){
        gpsProvider.onResume();
        locationProvider.onResume();
    }

    public void onPause(){
        gpsProvider.onPause();
        locationProvider.onPause();
    }

    public void unregister(Listener listener){
        if(listeners != null && listeners.indexOf(listener) != -1){
            listeners.remove(listener);
        }
    }
    public void register(Listener listener){
        if(listeners != null && listeners.indexOf(listener) == -1){
            listeners.add(listener);
        }
    }

    private void notifyLocationFetchFailed(@Nullable final ConnectionResult connectionResult) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (Listener listener : listeners) {
                    listener.onLocationFetchFailed(connectionResult);
                }
            }
        });
    }

    private void notifyLocationChanged(final Location location){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (Listener listener : listeners) {
                    listener.onLocationChanged(location);
                }
            }
        });
    }

    public interface Listener {
        void onLocationChanged(Location location);
        void onLocationFetchFailed(@Nullable ConnectionResult connectionResult);
    }
}
