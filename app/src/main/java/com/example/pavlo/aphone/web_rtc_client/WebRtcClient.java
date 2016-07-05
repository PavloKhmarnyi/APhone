package com.example.pavlo.aphone.web_rtc_client;

import android.util.Log;

import com.example.pavlo.aphone.R;
import com.example.pavlo.aphone.executor.LooperExecutor;
import com.example.pavlo.aphone.http_url_connection.AsyncHttpUrlConnection;
import com.example.pavlo.aphone.interfaces.AsyncHttpEvents;
import com.example.pavlo.aphone.interfaces.RoomParametersFetcherEvents;
import com.example.pavlo.aphone.interfaces.RtcClient;
import com.example.pavlo.aphone.interfaces.SignalingEvents;
import com.example.pavlo.aphone.interfaces.WebSocketChannelEvents;
import com.example.pavlo.aphone.parameters.RoomConnectionParameters;
import com.example.pavlo.aphone.parameters.SignalingParameters;
import com.example.pavlo.aphone.util.Config;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Created by pavlo on 22.06.16.
 */
public class WebRtcClient implements WebSocketChannelEvents, RtcClient {

    private static final String LOG_TAG = "WSRTCClient";

    private final LooperExecutor executor;

    private SignalingEvents events;
    private boolean initiator;

    private WebRtcChannelClient webRtcChannelClient;
    private RoomConnectionParameters connectionParameters;
    private ConnectionState roomState;

    private String messageUrl;
    private String leaveUrl;

    private enum ConnectionState {
        NEW, CONNECTED, CLOSED, ERROR
    };

    private enum MessageType {
        MESSAGE, LEAVE
    };

    public WebRtcClient(SignalingEvents events, LooperExecutor executor) {
        this.events = events;
        this.executor = executor;
        roomState = ConnectionState.NEW;
        executor.requestStart();
    }

    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                conectToRoomInternal();
            }
        });
    }

    @Override
    public void sendOfferSdp(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer sdp in non connected state!");
                    return;
                }

                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "offer");
                sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());

                if (connectionParameters.isLoopback()) {
                    SessionDescription sdpAnswer = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm("answer"),
                            sdp.description
                    );
                    events.onRemoteDescription(sdpAnswer);
                }
            }
        });
    }

    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (connectionParameters.isLoopback()) {
                    Log.e(LOG_TAG, "Sending answer in loopback mode.");
                    return;
                }
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                webRtcChannelClient.send(json.toString());
            }
        });
    }

    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "candidate");
                jsonPut(json, "label", candidate.sdpMLineIndex);
                jsonPut(json, "id", candidate.sdpMid);
                jsonPut(json, "candidate", candidate.sdp);

                if (initiator) {
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate in non connected state.");
                        return;
                    }
                    sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    if (connectionParameters.isLoopback()) {
                        events.onRemoteIceCandidate(candidate);
                    }
                } else {
                    webRtcChannelClient.send(json.toString());
                }
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
            }
        });
        executor.requestStop();
    }

    private void conectToRoomInternal() {
        String connectionUrl = getConnectionUrl(connectionParameters);
        Log.d(LOG_TAG, "Connect to room: " + connectionUrl);
        roomState = ConnectionState.NEW;
        webRtcChannelClient = new WebRtcChannelClient(executor, this);

        RoomParametersFetcherEvents callbacks = new RoomParametersFetcherEvents() {
            @Override
            public void onSignalingParametersReady(final SignalingParameters parameters) {
                WebRtcClient.this.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        WebRtcClient.this.signalingParametersReady(parameters);
                    }
                });
            }

            @Override
            public void onSignalingParametersError(String description) {
                WebRtcClient.this.reportError(description);
            }
        };

        new RoomParametersFetcher(connectionUrl, null, callbacks).makeRequest();
    }

    private void disconnectFromRoomInternal() {
        Log.d(LOG_TAG, "Disconnect. Room state: " + roomState);
        if (roomState == ConnectionState.CONNECTED) {
            Log.d(LOG_TAG, "Closing rooom.");
            sendPostMessage(MessageType.LEAVE, leaveUrl, null);
        }

        roomState = ConnectionState.CLOSED;
        if (webRtcChannelClient != null) {
            webRtcChannelClient.disconnect(true);
        }
    }

    private String getConnectionUrl(RoomConnectionParameters connectionParameters) {
        return connectionParameters.getRoomUrl() + "/" + Config.ROOM_JOIN + "/" + connectionParameters.getRoomId();
    }

    private String getMessageUrl(RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.getRoomUrl() + "/" + Config.ROOM_MESSAGE + "/" +
                connectionParameters.getRoomId() + "/" + signalingParameters.getClientId();
    }

    private String getLeaveUrl(RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
        return connectionParameters.getRoomUrl() + "/" + Config.ROOM_LEAVE + "/" +
                connectionParameters.getRoomId() + "/" + signalingParameters.getClientId();
    }

    private void signalingParametersReady(final SignalingParameters signalingParameters) {
        Log.d(LOG_TAG, "Room connection completed!");

        if (connectionParameters.isLoopback()
                && (!signalingParameters.isInitiator() || signalingParameters.getOfferSdp() != null)) {
            reportError("Loopback room is busy.");
        }

        if (connectionParameters.isLoopback() &&
                signalingParameters.isInitiator() &&
                signalingParameters.getOfferSdp() == null) {
            Log.w(LOG_TAG, "No offer SDP in room response.");
        }

        initiator = signalingParameters.isInitiator();
        messageUrl = getMessageUrl(connectionParameters, signalingParameters);
        leaveUrl = getLeaveUrl(connectionParameters, signalingParameters);

        Log.d(LOG_TAG, "Message URL: " + messageUrl);
        Log.d(LOG_TAG, "Leave URL: " + leaveUrl);

        roomState = ConnectionState.CONNECTED;

        events.onConnectedToRoom(signalingParameters);

        webRtcChannelClient.connect(signalingParameters.getWssUrl(), signalingParameters.getWssPostUrl());
        webRtcChannelClient.register(connectionParameters.getRoomId(), signalingParameters.getClientId());
    }

    private void reportError(final String errorMessage) {
        Log.e(LOG_TAG, "Error message: " + errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendPostMessage(final MessageType messageType, final String url, final String message) {
        String logInfo = url;

        if (message != null) {
            logInfo += ". Message: " + message;
        }

        Log.d(LOG_TAG, "C->GAE: " + logInfo);

        AsyncHttpUrlConnection httpUrlConnection = new AsyncHttpUrlConnection("POST", url, message, new AsyncHttpEvents() {
            @Override
            public void onHttpError(String errorMessage) {
                reportError("GAE POST error: " + errorMessage);
            }

            @Override
            public void onHttpComplete(String response) {
                if (messageType == MessageType.MESSAGE) {
                    try {
                        JSONObject roomJson = new JSONObject(response);
                        String result = roomJson.getString("result");
                        if (!result.equals("SUCCESS")) {
                            reportError("GAE POST error: " + result);
                        }
                    } catch (JSONException e) {
                        reportError("GAE POST JSON error: " + e.toString());
                    }
                }
            }
        });
        httpUrlConnection.send();
    }

    @Override
    public void onWebSocketMessage(final String message) {
        if (webRtcChannelClient.getState() != WebRtcChannelClient.WebSocketConnectionState.REGISTERED) {
            Log.e(LOG_TAG, "Got WebSocket message in non registered state.");
            return;
        }
        try {
            JSONObject json = new JSONObject(message);
            String messageText = json.getString("msg");
            String errorText = json.optString("error");

            if (messageText.length() > 0) {
                json = new JSONObject(messageText);
                String type = json.optString("type");

                if (type.equals("candidate")) {
                    IceCandidate candidate = new IceCandidate(
                            json.getString("id"),
                            json.getInt("label"),
                            json.getString("candidate"));
                    events.onRemoteIceCandidate(candidate);
                } else if (type.equals("answer")) {
                    if (initiator) {
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type),
                                json.getString("sdp"));
                        events.onRemoteDescription(sdp);
                    } else {
                        reportError("Received answer for call initiator: " + message);
                    }
                } else if (type.equals("offer")) {
                    if (!initiator) {
                        SessionDescription sdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(type),
                                json.getString("sdp"));
                        events.onRemoteDescription(sdp);
                    } else {
                        reportError("Received offer for call receiver: " + message);
                    }
                } else if (type.equals("bye")) {
                    events.onChannelClose();
                } else {
                    reportError("Unexpected WebSocket message: " + message);
                }
            } else {
                if (errorText != null && errorText.length() > 0) {
                    reportError("WebSocket error message: " + errorText);
                } else {
                    reportError("Unexpected WebSocket message: " + message);
                }
            }
        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketClose() {
        events.onChannelClose();
    }

    @Override
    public void onWebSocketError(final String description) {
        reportError("WebSocket error: " + description);
    }
}
