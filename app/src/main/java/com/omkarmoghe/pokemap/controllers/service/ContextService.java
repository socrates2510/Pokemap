package com.omkarmoghe.pokemap.controllers.service;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

public abstract class ContextService {
    private static final HandlerThread handlerThread = new HandlerThread("ContextService");
    static {
        handlerThread.start();
    }
    private static final Handler handler = new Handler(handlerThread.getLooper());
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    protected final Context context;
    private Runnable runOnPause = new Runnable() {
        public void run() {
            onPause();
        }
    };
    private Runnable runOnResume = new Runnable() {
        public void run() {
            onResume();
        }
    };
    private Runnable runOnStart = new Runnable() {
        public void run() {
            onStart();
        }
    };
    private Runnable runOnStop = new Runnable() {
        public void run() {
            onStop();
        }
    };

    public ContextService(Context context) {
        this.context = context;
    }

    public void onStart() {
    }

    public void onStop() {
    }

    public void onPause() {
    }

    public void onResume() {
    }


    public Context getContext() {
        return this.context;
    }

    public static void runOnUiThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public static void runOnServiceHandler(Runnable runnable) {
        handler.post(runnable);
    }

    public static boolean onUiThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    public static boolean onServiceThread() {
        return Looper.myLooper() == handlerThread.getLooper();
    }

    public static void assertOnServiceThread() {
        if (!onServiceThread()) {
            throw new RuntimeException("Must be on the service thread");
        }
    }

    public static Looper getServiceLooper() {
        return handlerThread.getLooper();
    }

    public static Handler getServiceHandler() {
        return handler;
    }

    private void invokeOnStart() {
        runOnServiceHandler(runOnStart);
    }

    private void invokeOnStop() {
        runOnServiceHandler(runOnStop);
    }

    private void invokeOnPause() {
        runOnServiceHandler(runOnPause);
    }

    private void invokeOnResume() {
        runOnServiceHandler(runOnResume);
    }
}
