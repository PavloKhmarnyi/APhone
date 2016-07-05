package com.example.pavlo.aphone.web_rtc_client;

import android.util.Log;

import com.example.pavlo.aphone.executor.LooperExecutor;
import com.example.pavlo.aphone.http_url_connection.AsyncHttpUrlConnection;
import com.example.pavlo.aphone.interfaces.AsyncHttpEvents;
import com.example.pavlo.aphone.interfaces.WebSocketChannelEvents;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

/**
 * Created by pavlo on 22.06.16.
 */
public class WebRtcChannelClient {

    private static final String LOG_TAG = "Web socket channel RTC client ";

    private final Object closeEventLoock = new Object();
    private final LooperExecutor executor;

    private WebSocketChannelEvents events;
    private WebSocketConnection webSocketConnection;
    private WebSocketObserver webSocketObserver;

    private String webSocketServerUrl;
    private String postServerUrl;
    private String roomId;
    private String clientId;

    private WebSocketConnectionState state;

    private boolean closeEvent;

    private final LinkedList<String> webSocketSendQueue;

    public enum WebSocketConnectionState {
        NEW, CONNECTED, REGISTERED, CLOSED, ERROR
    };

    public WebRtcChannelClient(LooperExecutor executor, WebSocketChannelEvents events) {
        this.executor = executor;
        this.events = events;
        roomId = null;
        clientId = null;
        webSocketSendQueue = new LinkedList<>();
        state = WebSocketConnectionState.NEW;
    }

    public WebSocketConnectionState getState() {
        return state;
    }

    public void connect(final String webSocketUrl, final String postUrl) {
        checkIfCalledOnValidThread();
        if (state != WebSocketConnectionState.NEW) {
            Log.d(LOG_TAG, "WebSocket is already connected!");
            return;
        }

        webSocketServerUrl = webSocketUrl;
        postServerUrl = postUrl;
        closeEvent = false;

        Log.d(LOG_TAG, "Connecting WebSocket to: " + webSocketServerUrl + ". Post URL: " + postUrl);

        webSocketConnection = new WebSocketConnection();
        webSocketObserver = new WebSocketObserver();

        try {
            webSocketConnection.connect(new URI(webSocketServerUrl), webSocketObserver);
        } catch (URISyntaxException e) {
            reportError("URI error: " + e.getMessage());
        } catch (WebSocketException e) {
            reportError("WebSocket connection error: " + e.getMessage());
        }
    }

    public void register(final String roomId, final String clientId) {
        checkIfCalledOnValidThread();
        this.roomId = roomId;
        this.clientId = clientId;
        if (state != WebSocketConnectionState.CONNECTED) {
            Log.w(LOG_TAG, "WebSocket register() in state " + state);
            return;
        }
        Log.d(LOG_TAG, "Register WebSocket to room: " + roomId + ", client: " + clientId);

        JSONObject json = new JSONObject();
        try {
            json.put("cmd", "register");
            json.put("roomid", roomId);
            json.put("clientid", clientId);
            Log.d(LOG_TAG, "C->WSS: " + json.toString());
            webSocketConnection.sendTextMessage(json.toString());

            state = WebSocketConnectionState.REGISTERED;

            for (String message : webSocketSendQueue) {
                send(message);
            }
            webSocketSendQueue.clear();
        } catch (JSONException e) {
            reportError("WebSocket register json error: " + e.getMessage());
        }
    }

    public void send(String message) {
        checkIfCalledOnValidThread();
        switch (state) {
            case NEW:
            case CONNECTED:
                Log.d(LOG_TAG, "WS ACC: " + message);
                webSocketSendQueue.add(message);
                return;
            case ERROR:
            case CLOSED:
                Log.e(LOG_TAG, "WebSocket send() in error or closed state: " + message);
                return;
            case REGISTERED:
                JSONObject json = new JSONObject();
                try {
                    json.put("cmd", "send");
                    json.put("msg", message);
                    message = json.toString();
                    Log.d(LOG_TAG, "C->WSS: " + message);
                    webSocketConnection.sendTextMessage(message);
                } catch (JSONException e) {
                    reportError("WebSocket send JSON error: " + e.getMessage());
                }
                break;
        }
        return;
    }

    public void post(String message) {
        checkIfCalledOnValidThread();
        sendWSSMessage("POST", message);
    }

    public void disconnect(boolean waitForComplete) {
        checkIfCalledOnValidThread();
        Log.d(LOG_TAG, "Disconnect WebSocket. State: " + state);
        if (state == WebSocketConnectionState.REGISTERED) {
            send("{\"type\": \"bye\"}");
            state = WebSocketConnectionState.CONNECTED;
            sendWSSMessage("DELETE", "");
        }

        if (state == WebSocketConnectionState.CONNECTED || state == WebSocketConnectionState.ERROR) {
            webSocketConnection.disconnect();
            state = WebSocketConnectionState.CLOSED;

            if (waitForComplete) {
                synchronized (closeEventLoock) {
                    while (!closeEvent) {
                        try {
                            closeEventLoock.wait();
                            break;
                        } catch (InterruptedException e) {
                            Log.e(LOG_TAG, "Wait error: " + e.getMessage());
                        }
                    }
                }
            }
        }
        Log.d(LOG_TAG, "Disconnecting WebSocket done!");
    }

    private void reportError(final String errorMessage) {
        Log.e(LOG_TAG, errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (state != WebSocketConnectionState.ERROR) {
                    state = WebSocketConnectionState.ERROR;
                    events.onWebSocketError(errorMessage);
                }
            }
        });
    }

    private void sendWSSMessage(final String method, final String message) {
        String postUrl = postServerUrl + "/" + roomId + "/" + clientId;
        Log.d(LOG_TAG, "WebSocket " + method + " : " + postUrl + " : " + message);

        AsyncHttpUrlConnection httpUrlConnection = new AsyncHttpUrlConnection(method,
                postUrl,
                message,
                new AsyncHttpEvents() {
            @Override
            public void onHttpError(String errorMessage) {
                reportError("WebSocket " + method + " error: " + errorMessage);
            }

            @Override
            public void onHttpComplete(String response) {

            }
        });
        httpUrlConnection.send();
    }

    private void checkIfCalledOnValidThread() {
        if (!executor.checkOnLooperThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    private class WebSocketObserver implements WebSocket.WebSocketConnectionObserver {
        @Override
        public void onOpen() {
            Log.d(LOG_TAG, "WebSocket connection opened to: " + webSocketServerUrl);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    state = WebSocketConnectionState.CONNECTED;

                    if (roomId != null && clientId != null) {
                        register(roomId, clientId);
                    }
                }
            });
        }

        @Override
        public void onClose(WebSocketCloseNotification webSocketCloseNotification, String s) {
            Log.d(LOG_TAG, "WebSocket connection closed. Code: " + webSocketCloseNotification +
            ", reason: " + s + ", state: " + state);

            synchronized (closeEventLoock) {
                closeEvent = true;
                closeEventLoock.notify();
            }
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (state != WebSocketConnectionState.CLOSED) {
                        state = WebSocketConnectionState.CLOSED;
                        events.onWebSocketClose();
                    }
                }
            });
        }

        @Override
        public void onTextMessage(String s) {
            Log.d(LOG_TAG, "WSS->C: " + s);
            final String message = s;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (state == WebSocketConnectionState.CONNECTED || state == WebSocketConnectionState.REGISTERED) {
                        events.onWebSocketMessage(message);
                    }
                }
            });
        }

        @Override
        public void onRawTextMessage(byte[] bytes) {

        }

        @Override
        public void onBinaryMessage(byte[] bytes) {

        }
    }
}
