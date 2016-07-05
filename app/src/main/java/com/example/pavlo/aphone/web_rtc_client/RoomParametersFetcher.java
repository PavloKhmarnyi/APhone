package com.example.pavlo.aphone.web_rtc_client;

import android.util.Log;

import com.example.pavlo.aphone.http_url_connection.AsyncHttpUrlConnection;
import com.example.pavlo.aphone.interfaces.AsyncHttpEvents;
import com.example.pavlo.aphone.interfaces.RoomParametersFetcherEvents;
import com.example.pavlo.aphone.parameters.SignalingParameters;
import com.example.pavlo.aphone.util.Config;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * Created by pavlo on 22.06.16.
 */
public class RoomParametersFetcher {

    private static final String LOG_TAG = "Room RTC client";

    private final RoomParametersFetcherEvents events;

    private final String roomUrl;
    private final String roomMessage;

    private AsyncHttpUrlConnection httpUrlConnection;

    public RoomParametersFetcher(String roomUrl, String roomMessage, final RoomParametersFetcherEvents events) {
        this.roomUrl = roomUrl;
        this.roomMessage = roomMessage;
        this.events = events;
    }

    public void makeRequest() {
        Log.d(LOG_TAG, "Connecting to room:" + roomUrl);
        httpUrlConnection = new AsyncHttpUrlConnection("POST", roomUrl, roomMessage, new AsyncHttpEvents() {
            @Override
            public void onHttpError(String errorMessage) {
                Log.e(LOG_TAG, "Room connection error: " + errorMessage);
                events.onSignalingParametersError(errorMessage);
            }

            @Override
            public void onHttpComplete(String response) {
                roomHttpResponseParse(response);
            }
        });
        httpUrlConnection.send();
    }

    public void roomHttpResponseParse(String response) {
        Log.d(LOG_TAG, "Room response: " + response);
        try {
            LinkedList<IceCandidate> iceCandidates = null;
            SessionDescription offerSdp = null;
            JSONObject roomJson = new JSONObject(response);

            String result = roomJson.getString("result");
            if (!result.equals("SUCCESS")) {
                events.onSignalingParametersError("Room response error: " + result);
                return;
            }

            response = roomJson.getString("params");
            roomJson = new JSONObject(response);
            String roomId = roomJson.getString("room_id");
            String clientId = roomJson.getString("client_id");
            String wssUrl = roomJson.getString("wss_url");
            String wssPostUrl = roomJson.getString("wss_post_url");
            boolean initiator = roomJson.getBoolean("is_initiator");

            if (!initiator) {
                iceCandidates = new LinkedList<IceCandidate>();
                String messagesString = roomJson.getString("messages");
                JSONArray messages = new JSONArray(messagesString);

                for (int i = 0; i < messages.length(); ++i) {
                    String messageString = messages.getString(i);
                    JSONObject message = new JSONObject(messageString);
                    String messageType = message.getString("type");
                    Log.d(LOG_TAG, "GAE->C #" + i + " : " + messageString);

                    if (messageType.equals("offer")) {
                        offerSdp = new SessionDescription(
                                SessionDescription.Type.fromCanonicalForm(messageType),
                                message.getString("sdp"));
                    } else if (messageType.equals("candidate")) {
                        IceCandidate candidate = new IceCandidate(
                                message.getString("id"),
                                message.getInt("label"),
                                message.getString("candidate"));
                        iceCandidates.add(candidate);
                    } else {
                        Log.d(LOG_TAG, "Unknown message: " + messagesString);
                    }
                }
            }

            Log.d(LOG_TAG, "RoomId: " + roomId + ". ClientId: " + clientId);
            Log.d(LOG_TAG, "Initiator: " + initiator);
            Log.d(LOG_TAG, "WSS url: " + wssUrl);
            Log.d(LOG_TAG, "WSS POST url: " + wssPostUrl);

            LinkedList<PeerConnection.IceServer> iceServers = iceServersFromPCConfig(roomJson.getString("pc_config"));
            boolean isTurnPresent = false;

            for (PeerConnection.IceServer server : iceServers) {
                Log.d(LOG_TAG, "IceServer: " + server);
                if (server.uri.startsWith("turn:")) {
                    isTurnPresent = true;
                    break;
                }
            }

            if (!isTurnPresent) {
                LinkedList<PeerConnection.IceServer> turnServers = requestTurnServers(roomJson.getString("turn_url"));

                for (PeerConnection.IceServer turnServer : turnServers) {
                    Log.d(LOG_TAG, "TurnServer: " + turnServer);
                    iceServers.add(turnServer);
                }
            }

            SignalingParameters parameters = SignalingParameters.
                    newBuilder().
                    setIceServers(iceServers).
                    setInitiator(initiator).
                    setClientId(clientId).
                    setWssUrl(wssUrl).
                    setWssPostUrl(wssPostUrl).
                    setOfferSdp(offerSdp).
                    setIceCandidates(iceCandidates).
                    biuld();

            events.onSignalingParametersReady(parameters);
        } catch (JSONException e) {
            events.onSignalingParametersError(
                    "Room JSON parsing error: " + e.toString());
        } catch (IOException e) {
            events.onSignalingParametersError("Room IO error: " + e.toString());
        }
    }

    private LinkedList<PeerConnection.IceServer> requestTurnServers(String url) throws IOException, JSONException{
        LinkedList<PeerConnection.IceServer> turnServers = new LinkedList<PeerConnection.IceServer>();
        Log.d(LOG_TAG, "Request TURN from: " + url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(Config.TURN_HTTP_TIMEOUT_MS);
        connection.setReadTimeout(Config.TURN_HTTP_TIMEOUT_MS);

        int responseCode = connection.getResponseCode();

        if (responseCode != 200) {
            throw new IOException("Non-200 response when requesting TURN server from "
                    + url + " : " + connection.getHeaderField(null));
        }

        InputStream inputStream = connection.getInputStream();
        String response = drainStream(inputStream);
        connection.disconnect();

        Log.d(LOG_TAG, "TURN response: " + response);

        JSONObject responseJson = new JSONObject(response);
        String username = responseJson.getString("username");
        String password = responseJson.getString("password");
        JSONArray turnUris = responseJson.getJSONArray("uris");

        for (int i = 0; i < turnUris.length(); i++) {
            String uri = turnUris.getString(i);
            turnServers.add(new PeerConnection.IceServer(uri, username, password));
        }

        return turnServers;
    }

    private LinkedList<PeerConnection.IceServer> iceServersFromPCConfig(String pcConfig) throws JSONException{
        JSONObject json = new JSONObject(pcConfig);
        JSONArray servers = json.getJSONArray("iceServers");

        LinkedList<PeerConnection.IceServer> ret = new LinkedList<PeerConnection.IceServer>();

        for (int i = 0; i < servers.length(); ++i) {
            JSONObject server = servers.getJSONObject(i);
            String url = server.getString("urls");
            String credential = server.has("credential") ? server.getString("credential") : "";
            ret.add(new PeerConnection.IceServer(url, "", credential));
        }

        return ret;
    }

    private static String drainStream(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");

        return scanner.hasNext() ? scanner.next() : "";
    }
}
