package com.example.pavlo.aphone.audio_manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.util.Log;

import com.example.pavlo.aphone.event_listeners.ProximitySensorListener;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by pavlo on 24.06.16.
 */
public class RtcAudioManager {

    private final static String LOG_TAG = "Rtc audio manager";

    private final Context context;
    private final Runnable onStateChangeListener;
    private final AudioDevice defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
    private final Set<AudioDevice> audioDevices = new HashSet<AudioDevice>();

    private AudioDevice selectedAudioDevice;
    private AudioManager audioManager;
    private BroadcastReceiver wiredHeadsetReceiver;

    private ProximitySensorListener proximitySensorListener;

    private boolean initialized;
    private boolean savedIsSpeakerPhoneOn = false;
    private boolean savedIsMicrophoneMute = false;

    private int savedAudioMode = AudioManager.MODE_INVALID;

    public enum AudioDevice {
        SPEAKER_PHONE,
        WIRED_HEADSET,
        EARPIECE,
    }

    public RtcAudioManager(Context context, Runnable deviceStatechangeListener) {
        this.context = context;
        onStateChangeListener = deviceStatechangeListener;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        proximitySensorListener = ProximitySensorListener.create(context, new Runnable() {
            @Override
            public void run() {
                onProximitySensorChangedState();
            }
        });
    }

    private void onProximitySensorChangedState() {
        if (audioDevices.size() == 2
                && audioDevices.contains(AudioDevice.EARPIECE)
                && audioDevices.contains(
                AudioDevice.SPEAKER_PHONE)) {
            if (proximitySensorListener.sensorReportNearState()) {
                setAudioDevice(AudioDevice.EARPIECE);
            } else {
                setAudioDevice(AudioDevice.SPEAKER_PHONE);
            }
        }
    }

    public void init() {
        Log.d(LOG_TAG, "init");
        if (initialized) {
            return;
        }

        savedAudioMode = audioManager.getMode();
        savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
        savedIsMicrophoneMute = audioManager.isMicrophoneMute();

        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        setMicrophoneMute(false);
        updateAudioDeviceState(true);
        registerForWiredHeadsetIntentBroadcast();

        initialized = true;
    }

    public void close() {
        Log.d(LOG_TAG, "close");
        if (!initialized) {
            return;
        }

        unregisterForWiredHeadsetIntentBroadcast();

        setSpeakerphoneOn(savedIsSpeakerPhoneOn);
        setMicrophoneMute(savedIsMicrophoneMute);
        audioManager.setMode(savedAudioMode);
        audioManager.abandonAudioFocus(null);

        if (proximitySensorListener != null) {
            proximitySensorListener.stop();
            proximitySensorListener = null;
        }

        initialized = false;
    }

    public void setAudioDevice(AudioDevice device) {
        Log.d(LOG_TAG, "setAudioDevice(device=" + device + ")");

        switch (device) {
            case SPEAKER_PHONE:
                setSpeakerphoneOn(true);
                selectedAudioDevice = AudioDevice.SPEAKER_PHONE;
                break;
            case EARPIECE:
                setSpeakerphoneOn(false);
                selectedAudioDevice = AudioDevice.EARPIECE;
                break;
            case WIRED_HEADSET:
                setSpeakerphoneOn(false);
                selectedAudioDevice = AudioDevice.WIRED_HEADSET;
                break;
            default:
                Log.e(LOG_TAG, "Invalid audio device selection");
                break;
        }
        onAudioManagerChangedState();
    }

    public Set<AudioDevice> getAudioDevices() {
        return Collections.unmodifiableSet(new HashSet<AudioDevice>(audioDevices));
    }

    public AudioDevice getSelectedAudioDevice() {
        return selectedAudioDevice;
    }

    private void registerForWiredHeadsetIntentBroadcast() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);

        /** Receiver which handles changes in wired headset availability. */
        wiredHeadsetReceiver = new BroadcastReceiver() {
            private static final int STATE_UNPLUGGED = 0;
            private static final int STATE_PLUGGED = 1;
            private static final int HAS_NO_MIC = 0;
            private static final int HAS_MIC = 1;

            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra("state", STATE_UNPLUGGED);
                int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
                String name = intent.getStringExtra("name");
                Log.d(LOG_TAG, "BroadcastReceiver.onReceive"
                        + ": "
                        + "a=" + intent.getAction()
                        + ", s=" + (state == STATE_UNPLUGGED ? "unplugged" : "plugged")
                        + ", m=" + (microphone == HAS_MIC ? "mic" : "no mic")
                        + ", n=" + name
                        + ", sb=" + isInitialStickyBroadcast());

                boolean hasWiredHeadset = (state == STATE_PLUGGED) ? true : false;
                switch (state) {
                    case STATE_UNPLUGGED:
                        updateAudioDeviceState(hasWiredHeadset);
                        break;
                    case STATE_PLUGGED:
                        if (selectedAudioDevice != AudioDevice.WIRED_HEADSET) {
                            updateAudioDeviceState(hasWiredHeadset);
                        }
                        break;
                    default:
                        Log.e(LOG_TAG, "Invalid state");
                        break;
                }
            }
        };

        context.registerReceiver(wiredHeadsetReceiver, filter);
    }

    private void unregisterForWiredHeadsetIntentBroadcast() {
        context.unregisterReceiver(wiredHeadsetReceiver);
        wiredHeadsetReceiver = null;
    }

    private void setSpeakerphoneOn(boolean on) {
        boolean wasOn = audioManager.isSpeakerphoneOn();
        if (wasOn == on) {
            return;
        }
        audioManager.setSpeakerphoneOn(on);
    }

    private void setMicrophoneMute(boolean on) {
        boolean wasMuted = audioManager.isMicrophoneMute();
        if (wasMuted == on) {
            return;
        }
        audioManager.setMicrophoneMute(on);
    }

    private boolean hasEarpiece() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private void updateAudioDeviceState(boolean hasWiredHeadset) {
        audioDevices.clear();
        if (hasWiredHeadset) {
            audioDevices.add(AudioDevice.WIRED_HEADSET);
        }
        else {
            audioDevices.add(AudioDevice.SPEAKER_PHONE);
            if (hasEarpiece())  {
                audioDevices.add(AudioDevice.EARPIECE);
            }
        }
        Log.d(LOG_TAG, "audioDevices: " + audioDevices);

        if (hasWiredHeadset) {
            setAudioDevice(AudioDevice.WIRED_HEADSET);
        } else {
            setAudioDevice(defaultAudioDevice);
        }
    }

    private void onAudioManagerChangedState() {
        Log.d(LOG_TAG, "onAudioManagerChangedState: devices=" + audioDevices + ", selected=" + selectedAudioDevice);
        if (audioDevices.size() == 2) {
            proximitySensorListener.start();
        } else if (audioDevices.size() == 1) {
            proximitySensorListener.stop();
        } else {
            Log.e(LOG_TAG, "Invalid device list");
        }

        if (onStateChangeListener != null) {
            onStateChangeListener.run();
        }
    }

    @Deprecated
    private boolean hasWiredHeadset() {
        return audioManager.isWiredHeadsetOn();
    }
}
