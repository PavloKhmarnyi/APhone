package com.example.pavlo.aphone.parameters;

/**
 * Created by pavlo on 22.06.16.
 */
public class RoomConnectionParameters {

    private String roomUrl;
    private String roomId;
    private boolean loopback;

    public RoomConnectionParameters(String roomUrl, String roomId, boolean loopback) {
        this.roomUrl = roomUrl;
        this.roomId = roomId;
        this.loopback = loopback;
    }

    public String getRoomUrl() {
        return roomUrl;
    }

    public String getRoomId() {
        return roomId;
    }

    public boolean isLoopback() {
        return loopback;
    }
}
