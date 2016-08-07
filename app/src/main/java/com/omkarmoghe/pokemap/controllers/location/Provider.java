package com.omkarmoghe.pokemap.controllers.location;

import android.location.Location;

/**
 * Created by chris on 8/7/2016.
 */

public interface Provider {

    public interface ProviderListener {
        void onProviderLocation(Location location);

        void onProviderStatus(ServiceStatus serviceStatus);
    }

    void onStart();

    void onStop();

    void onResume();

    void onPause();

    void setListener(ProviderListener providerListener);
}
