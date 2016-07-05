package com.example.pavlo.aphone.executor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.Executor;

/**
 * Created by pavlo on 25.06.16.
 */
public class LooperExecutor extends Thread implements Executor {

    private static final String LOG_TAG = "Looper executor";

    private final Object looperStartedEvent = new Object();

    private Handler handler = null;
    private boolean running = false;
    private long threadId;

    @Override
    public void run() {
        Looper.prepare();
        synchronized (looperStartedEvent) {
            Log.d(LOG_TAG, "Looper thread started.");
            handler = new Handler();
            threadId = Thread.currentThread().getId();
            looperStartedEvent.notify();
        }
        Looper.loop();
    }

    public synchronized void requestStart() {
        if (running) {
            return;
        }
        running = true;
        handler = null;
        start();

        synchronized (looperStartedEvent) {
            while (handler == null) {
                try {
                    looperStartedEvent.wait();
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "Can not started looper thread!");
                    running = false;
                }
            }
        }
    }

    public synchronized void requestStop() {
        if (!running) {
            return;
        }
        running = false;
        handler.post(new Runnable() {
            @Override
            public void run() {
                Looper.myLooper().quit();
                Log.d(LOG_TAG, "Looper thread finished!");
            }
        });
    }

    public boolean checkOnLooperThread() {
        return Thread.currentThread().getId() == threadId;
    }

    @Override
    public synchronized void execute(final Runnable command) {
        if (!running) {
            Log.w(LOG_TAG, "Running looper executor without calling requestStart()");
            return;
        }
        if (checkOnLooperThread()) {
            command.run();
        } else {
            handler.post(command);
        }
    }
}
