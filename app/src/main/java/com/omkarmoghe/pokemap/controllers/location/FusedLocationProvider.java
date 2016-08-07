package com.omkarmoghe.pokemap.controllers.location;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.omkarmoghe.pokemap.controllers.service.ContextService;

/**
 * Created by chris on 8/7/2016.
 */

public class FusedLocationProvider extends ContextService implements Provider {
    private static final String TAG = "FusedLocationProvider";

    private final GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest = new LocationRequest();
    private ProviderListener mProviderListener = null;

    public FusedLocationProvider(final Context context) {
        super(context);
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        GoogleApiClient.ConnectionCallbacks connectionListener = new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(@Nullable Bundle bundle) {
                updateStatus(ServiceStatus.INITIALIZED);
                startProvider();
            }

            @Override
            public void onConnectionSuspended(int i) {
                FusedLocationProvider.this.updateStatus(ServiceStatus.FAILED);
                stopProvider();
            }
        };

        GoogleApiClient.OnConnectionFailedListener failedListener = new GoogleApiClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                Log.e(TAG, "Failed to fetch user location. Connection result: " + connectionResult.getErrorMessage());
                switch (connectionResult.getErrorCode()) {
                    case 19:
                        FusedLocationProvider.this.updateStatus(ServiceStatus.PERMISSION_DENIED);
                        return;
                    default:
                        FusedLocationProvider.this.updateStatus(ServiceStatus.FAILED);

                }

            }
        };

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(connectionListener)
                .addOnConnectionFailedListener(failedListener)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onStop() {

    }

    @Override
    public void onResume(){
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onPause(){
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void setListener(ProviderListener providerListener) {
        this.mProviderListener = providerListener;
    }

    private void updateStatus(ServiceStatus status) {
        ProviderListener listener = mProviderListener;
        if (listener != null) {
            listener.onProviderStatus(status);
        }
    }

    private void startProvider() {
        try {
            LocationServices.FusedLocationApi
                    .requestLocationUpdates(mGoogleApiClient, this.mLocationRequest, this.mFusedListener,
                            ContextService.getServiceLooper())
                    .setResultCallback(new ResultCallback<Status>() {
                        public void onResult(Status status) {
                            if (status.isSuccess()) {
                                FusedLocationProvider.this.updateStatus(ServiceStatus.RUNNING);
                            } else {
                                FusedLocationProvider.this.updateStatus(ServiceStatus.FAILED);
                            }
                        }
                    });
        } catch (SecurityException e) {
            updateStatus(ServiceStatus.PERMISSION_DENIED);
        }
    }

    private void stopProvider() {
        LocationServices.FusedLocationApi
                .removeLocationUpdates(mGoogleApiClient, this.mFusedListener)
                .setResultCallback(new ResultCallback<Status>() {

                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            FusedLocationProvider.this.updateStatus(ServiceStatus.STOPPED);
                        }
                    }
                });
    }

    private LocationCallback mFusedListener = new LocationCallback() {
        public void onLocationResult(LocationResult result) {
            Location location = result.getLastLocation();

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
                return;
            }

            boolean isValidLocation = LocationServices.FusedLocationApi
                    .getLocationAvailability(mGoogleApiClient)
                    .isLocationAvailable();

            if (location != null && isValidLocation) {
                ProviderListener listener = FusedLocationProvider.this.mProviderListener;
                if (listener != null) {
                    listener.onProviderLocation(location);
                }
            }
        }

        public void onLocationAvailability(LocationAvailability locationAvailability) {
            if (locationAvailability.isLocationAvailable()) {
                updateStatus(ServiceStatus.RUNNING);
            } else {
                updateStatus(ServiceStatus.PERMISSION_DENIED);
            }
        }
    };

}
