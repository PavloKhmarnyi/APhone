package com.example.pavlo.aphone.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.pavlo.aphone.R;
import com.example.pavlo.aphone.audio_manager.RtcAudioManager;
import com.example.pavlo.aphone.executor.LooperExecutor;
import com.example.pavlo.aphone.interfaces.PeerConnectionEvents;
import com.example.pavlo.aphone.interfaces.SignalingEvents;
import com.example.pavlo.aphone.parameters.PeerConnectionParameters;
import com.example.pavlo.aphone.parameters.RoomConnectionParameters;
import com.example.pavlo.aphone.parameters.SignalingParameters;
import com.example.pavlo.aphone.permissions_manager.PermissionsManager;
import com.example.pavlo.aphone.util.Config;
import com.example.pavlo.aphone.util.PeerConnectionUtilities;
import com.example.pavlo.aphone.web_rtc_client.PeerConnectionClient;
import com.example.pavlo.aphone.web_rtc_client.WebRtcClient;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;

public class MainActivity extends AppCompatActivity implements SignalingEvents,
        PeerConnectionEvents, View.OnClickListener{

    private static final String LOG_TAG = "Main activity log " ;

    private EditText roomNameEditText;
    private TextView callStatusTextView;
    private ProgressBar callStatusProgressBar;
    private ImageView connectButton;
    private ImageView disconnectButton;

    private PeerConnectionClient peerConnectionClient = null;
    private WebRtcClient webRtcClient;
    private SignalingParameters signalingParameters;
    private RtcAudioManager audioManager = null;

    private RoomConnectionParameters roomConnectionParameters;
    private PeerConnectionParameters peerConnectionParameters;

    private Toast toast;

    private boolean activityRunning;
    private boolean iceConnected;
    private boolean isError;

    private long callStartedTimeMs = 0;

    private String roomId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        iceConnected = false;
        signalingParameters = null;

        initViewComponents();
        PermissionsManager.requestPermissions(MainActivity.this);
    }

    @Override
    public void onDestroy() {
        disconnect();
        if (toast != null) {
            toast.cancel();
        }
        activityRunning = false;
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResult) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResult);

        String message = (requestCode == Config.PERMISSION_REQUEST_CODE) ?
                getString(R.string.microphone_available) :
                getString(R.string.microphone_not_available);

        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    private void initViewComponents() {
        roomNameEditText = (EditText) findViewById(R.id.roomNameEditText);
        callStatusTextView = (TextView) findViewById(R.id.callStatusTextView);
        callStatusProgressBar = (ProgressBar) findViewById(R.id.callStatusProgressBar);
        connectButton = (ImageView) findViewById(R.id.connectButton);
        disconnectButton = (ImageView) findViewById(R.id.disconnectButton);

        callStatusTextView.setVisibility(View.INVISIBLE);
        callStatusProgressBar.setVisibility(View.INVISIBLE);
        disconnectButton.setVisibility(View.INVISIBLE);

        connectButton.setOnClickListener(this);
        disconnectButton.setOnClickListener(this);
    }

    private void startCall() {
        if (webRtcClient == null) {
            Log.d(LOG_TAG, "WebRTC cliaent is not alocated for a call!");
            return;
        }
        callStartedTimeMs = System.currentTimeMillis();
        webRtcClient.connectToRoom(roomConnectionParameters);

        audioManager = new RtcAudioManager(MainActivity.this, new Runnable() {
            @Override
            public void run() {
                onAudioManagerChangedState();
            }
        });

        Log.d(LOG_TAG, "Initializing the audio manager...");
        audioManager.init();
    }

    private void onAudioManagerChangedState() {
        // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
        // is active.
    }

    private void callConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Log.i(LOG_TAG, "Call connected: delay=" + delta + "ms");
        if (peerConnectionClient == null || isError) {
            Log.w(LOG_TAG, "Call is connected in closed or error state");
            return;
        }
        peerConnectionClient.enableStatsEvents(true, Config.STAT_CALLBACK_PERIOD);
    }

    private void disconnect() {
        activityRunning = false;
        if (webRtcClient != null) {
            webRtcClient.disconnectFromRoom();
            webRtcClient = null;
        }
        if (peerConnectionClient != null) {
            peerConnectionClient.closeInternal();
            peerConnectionClient = null;
        }
        if (audioManager != null) {
            audioManager.close();
            audioManager = null;
        }
        if (iceConnected && !isError) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (callStatusProgressBar.getVisibility() == View.VISIBLE) {
                    callStatusProgressBar.setVisibility(View.INVISIBLE);
                }
                if (callStatusTextView.getVisibility() == View.VISIBLE) {
                    callStatusTextView.setVisibility(View.INVISIBLE);
                }
                if (disconnectButton.getVisibility() == View.VISIBLE) {
                    disconnectButton.setVisibility(View.INVISIBLE);
                }
            }
        });
        //finish();
    }

    private void disconnectWithErrorMessage(final String errorMessage) {
        if (!activityRunning) {
            Log.e(LOG_TAG, "Critical error: " + errorMessage);
            disconnect();
            Toast.makeText(MainActivity.this, "Disconnect from " + roomId + " room!", Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Connection error!")
                    .setMessage(errorMessage)
                    .setCancelable(false)
                    .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            disconnect();
                        }
                    }).create().show();
        }
    }

    private void reportError(final String description) {
        if (!isError) {
            isError = true;
            disconnectWithErrorMessage(description);
        }
    }

    private void onConnectedToRoomInternal(final SignalingParameters params) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;

        signalingParameters = params;
        Toast.makeText(this, "Creating peer connection, delay=" + delta + "ms", Toast.LENGTH_SHORT).show();
        Log.d(LOG_TAG, "Creating peer connection, delay=" + delta + "ms");
        peerConnectionClient.createPeerConnection(signalingParameters);

        if (signalingParameters.isInitiator()) {
            Toast.makeText(this, "Creating OFFER...", Toast.LENGTH_SHORT).show();
            Log.d(LOG_TAG, "Creating OFFER...");
            peerConnectionClient.createOffer();
        } else {
            if (params.getOfferSdp() != null) {
                peerConnectionClient.setRemoteDescription(params.getOfferSdp());
                Toast.makeText(this, "Creating ANSWER...", Toast.LENGTH_SHORT).show();
                Log.d(LOG_TAG, "Creating ANSWER...");
                peerConnectionClient.createAnswer();
            }
            if (params.getIceCandidates() != null) {
                for (IceCandidate candidate : params.getIceCandidates()) {
                    peerConnectionClient.addRemoteIceCandidate(candidate);
                }
            }
        }
        if (callStatusProgressBar.getVisibility() == View.VISIBLE) {
            callStatusProgressBar.setVisibility(View.INVISIBLE);
        }
        if (disconnectButton.getVisibility() == View.INVISIBLE) {
            disconnectButton.setVisibility(View.VISIBLE);
        }
        if (callStatusTextView.getVisibility() == View.INVISIBLE) {
            callStatusTextView.setVisibility(View.VISIBLE);
        }
        callStatusTextView.setText("Connected to " + roomId + " room.");
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.connectButton:
                roomId = roomNameEditText.getText().toString();
                callStatusProgressBar.setVisibility(View.VISIBLE);
                callStatusTextView.setVisibility(View.VISIBLE);
                callStatusTextView.setText("CREATE CONNECTION...");

                peerConnectionParameters = PeerConnectionParameters.newBuilder().
                        setAudioStartBitrate(Config.AUDIO_BITRATE).
                        setAudioCodec(PeerConnectionUtilities.AUDIO_CODEC_OPUS).
                        setAecDump(false).
                        setNoAudioProcessing(false).
                        setUseOpenSLES(true).
                        setLoopback(false).
                        build();

                webRtcClient = new WebRtcClient(this, new LooperExecutor());

                roomConnectionParameters = new RoomConnectionParameters(Config.ROOM_URL, roomId, false);

                peerConnectionClient = PeerConnectionClient.getInstance();
                peerConnectionClient.createPeerConnectionFactory(this, peerConnectionParameters, this);
                startCall();
                break;
            case R.id.disconnectButton:
                disconnect();
                Toast.makeText(MainActivity.this, "Disconnect from " + roomId + " room!", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // SignalingEvents implementation

    @Override
    public void onConnectedToRoom(final SignalingParameters parameters) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onConnectedToRoomInternal(parameters);
            }
        });
    }

    @Override
    public void onRemoteDescription(final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(LOG_TAG, "Received remote SDP for non-initilized peer connection.");
                    return;
                }
                Log.d(LOG_TAG, "Received remote " + sdp.type + ", delay=" + delta + "ms");
                Toast.makeText(MainActivity.this, "Received remote " + sdp.type + ", delay=" + delta + "ms",
                        Toast.LENGTH_SHORT).show();;
                peerConnectionClient.setRemoteDescription(sdp);

                if (!signalingParameters.isInitiator()) {
                    Log.d(LOG_TAG, "Creating ANSWER...");
                    Toast.makeText(MainActivity.this, "Creating ANSWER...", Toast.LENGTH_SHORT).show();
                    peerConnectionClient.createAnswer();
                }
            }
        });
    }

    @Override
    public void onRemoteIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (peerConnectionClient == null) {
                    Log.e(LOG_TAG, "Received ICE candidate for non-initilized peer connection.");
                    return;
                }
                peerConnectionClient.addRemoteIceCandidate(candidate);
            }
        });
    }

    @Override
    public void onChannelClose() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "Remote end hung up; dropping PeerConnection");
                Toast.makeText(MainActivity.this, "Remote end hung up; dropping PeerConnection", Toast.LENGTH_SHORT).show();
                disconnect();
            }
        });
    }

    @Override
    public void onChannelError(final String description) {
        reportError(description);
    }

    // PeerConnectionEvents implementation

    @Override
    public void onLocalDescription(final SessionDescription sdp) {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (webRtcClient != null) {
                    Log.d(LOG_TAG, "Sending " + sdp.type + ", delay=" + delta + "ms");
                    Toast.makeText(MainActivity.this, "Sending " + sdp.type + ", delay=" + delta + "ms",
                            Toast.LENGTH_SHORT).show();
                    if (signalingParameters.isInitiator()) {
                        webRtcClient.sendOfferSdp(sdp);
                    } else {
                        webRtcClient.sendAnswerSdp(sdp);
                    }
                }
            }
        });
    }

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (webRtcClient != null) {
                    webRtcClient.sendLocalIceCandidate(candidate);
                }
            }
        });
    }

    @Override
    public void onIceConnected() {
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "ICE connected, delay=" + delta + "ms");
                Toast.makeText(MainActivity.this, "ICE connected, delay=" + delta + "ms", Toast.LENGTH_SHORT).show();
                iceConnected = true;
                callConnected();
            }
        });
    }

    @Override
    public void onIceDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "ICE disconnected");
                Toast.makeText(MainActivity.this, "ICE disconnected", Toast.LENGTH_SHORT).show();
                iceConnected = false;
                disconnect();
            }
        });
    }

    @Override
    public void onPeerConnectionClosed() {

    }

    @Override
    public void onPeerConnectionStatsReady(final StatsReport[] reports) {

    }

    @Override
    public void onPeerConnectionError(final String description) {
        reportError(description);
    }
}
