package com.omkarmoghe.pokemap.controllers.location;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.omkarmoghe.pokemap.controllers.service.ContextService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by chris on 8/7/2016.
 */
@SuppressWarnings("deprecation")
public class GpsProvider extends ContextService implements Provider {
    private static final String TAG = "GpsProvider";

    private static final boolean ENABLE_VERBOSE_LOGS = false;
    private boolean firstLocationUpdate = ENABLE_VERBOSE_LOGS;
    private LocationManager locationManager;
    private final String provider = LocationManager.GPS_PROVIDER;
    private ProviderListener providerListener = null;
    private boolean running = ENABLE_VERBOSE_LOGS;
    private final float updateDistance;
    private final int updateTime;

    public GpsProvider(Context context, int updateTime, float updateDistance) {
        super(context);
        this.updateTime = updateTime;
        this.updateDistance = updateDistance;
    }

    @Override
    public void onStart() {
        locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onStop() {
        locationManager = null;
    }

    @Override
    public void onResume() {
        firstLocationUpdate = true;
        ServiceStatus statusFailed = ServiceStatus.FAILED;
        try {
            locationManager.requestLocationUpdates(provider, (long) updateTime, updateDistance,
                    listener, ContextService.getServiceLooper());
            Log.d(TAG, "Location manager initialized");
            if (provider.equals("gps")) {
                locationManager.addGpsStatusListener(gpsStatusListener);
            }
            running = true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not request " + provider + " updates", e);
        } catch (SecurityException e2) {
            Log.e(TAG, "Not allowed to access " + provider + " for updates", e2);
            statusFailed = ServiceStatus.PERMISSION_DENIED;
        }

        ServiceStatus statusFailedCapture = statusFailed;
        if (running) {
            updateStatus(ServiceStatus.INITIALIZED);
            try {
                updateLocation(locationManager.getLastKnownLocation(this.provider));
                return;
            } catch (SecurityException e3) {
                return;
            }
        }
        updateStatus(statusFailedCapture);
    }

    @Override
    public void onPause() {
        if (running) {
            try {
                locationManager.removeUpdates(listener);
                running = ENABLE_VERBOSE_LOGS;
            } catch (SecurityException e) {
                Log.e(TAG, "Not allowed to access " + this.provider + " for updates", e);
            }
            updateStatus(ServiceStatus.STOPPED);
        }
    }


    @Override
    public void setListener(ProviderListener providerListener) {
        this.providerListener = providerListener;
    }

    private void updateStatus(ServiceStatus status) {
        ProviderListener listener = providerListener;
        if (listener != null) {
            listener.onProviderStatus(status);
        }
    }

    private void updateLocation(Location location) {
        ProviderListener listener = providerListener;
        if (listener != null) {
            if (this.firstLocationUpdate) {
                this.firstLocationUpdate = ENABLE_VERBOSE_LOGS;
                updateStatus(ServiceStatus.RUNNING);
            }
            listener.onProviderLocation(location);
        }
    }

    private void updateGpsStatus(int timeToFix, GpsSatellite[] satellites) {
        ProviderListener listener = providerListener;
        if (listener != null && (listener instanceof GpsProviderListener)) {
            ((GpsProviderListener) listener).onGpsStatusUpdate(timeToFix, satellites);
        }
    }

    public interface GpsProviderListener extends ProviderListener {
        @SuppressWarnings("deprecation")
        void onGpsStatusUpdate(int i, GpsSatellite[] gpsSatelliteArr);
    }

    private final GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
        private GpsSatellite[] getSatellites(GpsStatus gpsStatus) {
            List<GpsSatellite> list = new ArrayList<>();
            for (GpsSatellite sat : gpsStatus.getSatellites()) {
                list.add(sat);
            }
            return list.toArray(new GpsSatellite[list.size()]);
        }

        public void onGpsStatusChanged(int event) {
            if (running) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
                    return;
                }
                GpsStatus status = locationManager.getGpsStatus(null);
                updateGpsStatus(status.getTimeToFirstFix(), getSatellites(status));
            }
        }
    };

    private LocationListener listener = new LocationListener() {
        public void onLocationChanged(Location location) {
            if (running) {
                updateLocation(location);
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
            updateStatus(ServiceStatus.RUNNING);
        }

        public void onProviderDisabled(String provider) {
            updateStatus(ServiceStatus.PERMISSION_DENIED);
        }
    };
}
