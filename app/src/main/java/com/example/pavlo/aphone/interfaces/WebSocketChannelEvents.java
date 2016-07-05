package com.example.pavlo.aphone.interfaces;

/**
 * Created by pavlo on 22.06.16.
 */
public interface WebSocketChannelEvents {

    public void onWebSocketMessage(final String message);

    public void onWebSocketClose();

    public void onWebSocketError(final String description);
}
