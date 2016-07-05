package com.example.pavlo.aphone.util;

/**
 * Created by pavlo on 22.06.16.
 */
public class Config {

    public static final String ROOM_URL = "https://apprtc.appspot.com";

    public static final String ROOM_JOIN = "join";
    public static final String ROOM_MESSAGE = "message";
    public static final String ROOM_LEAVE = "leave";

    public static final int STAT_CALLBACK_PERIOD = 1000;
    public static final int HTTP_TIMEOUT_MS = 8000;
    public static final int TURN_HTTP_TIMEOUT_MS = 5000;
    private static final int CLOSE_TIMEOUT = 1000;

    public static final int PERMISSION_REQUEST_CODE = 0;
    public static final int AUDIO_BITRATE = 32;
}
