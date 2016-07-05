package com.example.pavlo.aphone.web_rtc_client;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.pavlo.aphone.executor.LooperExecutor;
import com.example.pavlo.aphone.interfaces.PeerConnectionEvents;
import com.example.pavlo.aphone.parameters.PeerConnectionParameters;
import com.example.pavlo.aphone.parameters.SignalingParameters;
import com.example.pavlo.aphone.util.PeerConnectionUtilities;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.StatsObserver;
import org.webrtc.StatsReport;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pavlo on 22.06.16.
 */
public class PeerConnectionClient {

    public static final String LOG_TAG = "Peer connection client ";

    private static final PeerConnectionClient instance = new PeerConnectionClient();

    private final PCObserver pcObserver = new PCObserver();
    private final SDPObserver sdpObserver = new SDPObserver();

    private final LooperExecutor executor;

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private PeerConnectionFactory.Options options = null;
    private PeerConnectionParameters peerConnectionParameters;
    private PeerConnectionEvents events;

    private LinkedList<IceCandidate> queuedRemoteCandidates;

    private SignalingParameters signalingParameters;

    private SessionDescription localSdp;

    private MediaConstraints pcConstraints;
    private MediaConstraints audioConstraints;
    private MediaConstraints sdpMediaConstraints;

    private MediaStream mediaStream;

    private ParcelFileDescriptor aecDumpFileDescriptor;

    private boolean preferIsac;
    private boolean isError;
    private boolean isInitiator;

    private Timer statsTimer;

    private PeerConnectionClient() {
        executor = new LooperExecutor();
        executor.requestStart();
    }

    public static PeerConnectionClient getInstance() {
        return instance;
    }

    public void setPeerConnectionFactoryOptions(PeerConnectionFactory.Options options) {
        this.options = options;
    }

    public void createPeerConnectionFactory(final Context context,
                                            final PeerConnectionParameters parameters,
                                            final PeerConnectionEvents events) {
        this.peerConnectionParameters = parameters;
        this.events = events;

        factory = null;
        peerConnection = null;
        preferIsac = false;
        isError = false;
        queuedRemoteCandidates = null;
        localSdp = null;
        mediaStream = null;
        statsTimer = new Timer();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                createPeerConnectionFactoryInternal(context);
            }
        });
    }

    public void createPeerConnection(final SignalingParameters signalingParameters) {
        if (peerConnectionParameters == null) {
            return;
        }
        this.signalingParameters = signalingParameters;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                createMediaConstraintsInternal();
                createPeerConnectionInternal();
            }
        });
    }

    private void createPeerConnectionFactoryInternal(Context context) {
        isError = false;
        preferIsac = false;

        if (peerConnectionParameters.getAudioCodec() != null &&
                peerConnectionParameters.getAudioCodec().equals(PeerConnectionUtilities.AUDIO_CODEC_ISAC)) {
            preferIsac = true;
        }

        if (!PeerConnectionFactory.initializeAndroidGlobals(context, true, false, false, null)) {
            events.onPeerConnectionError("Failed to initialize Android Globals");
        }

        if (options != null) {
            Log.d(LOG_TAG, "Factoty networkIgnoreMask option: " + options.networkIgnoreMask);
        }
        factory = new PeerConnectionFactory();
        Log.d(LOG_TAG, "Peer connection factory created!");
    }

    private void createMediaConstraintsInternal() {
        pcConstraints = new MediaConstraints();

        if (peerConnectionParameters.isLoopback()) {
            pcConstraints.optional.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionUtilities.DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "false"));
        } else {
            pcConstraints.optional.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionUtilities.DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));
        }

        audioConstraints = new MediaConstraints();
        Log.d(LOG_TAG, "");

        if (peerConnectionParameters.isNoAudioProcessing()) {
            Log.d(LOG_TAG, "");
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionUtilities.AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionUtilities.AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionUtilities.AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionUtilities.AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
        }

        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(PeerConnectionUtilities.OFFER_TO_RECIEVE_AUDIO, "true"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(PeerConnectionUtilities.OFFER_TO_RECIEVE_VIDEO, "false"));
    }

    private void createPeerConnectionInternal() {
        if (factory == null || isError) {
            Log.d(LOG_TAG, "PeerConnection factory is not created!");
            return;
        }

        queuedRemoteCandidates = new LinkedList<IceCandidate>();

        PeerConnection.RTCConfiguration rtcConfiguration =
                new PeerConnection.RTCConfiguration(signalingParameters.getIceServers());

        rtcConfiguration.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfiguration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;

        peerConnection = factory.createPeerConnection(rtcConfiguration, pcConstraints, pcObserver);
        isInitiator = false;

        mediaStream = factory.createLocalMediaStream(PeerConnectionUtilities.LOCAL_MEDIA_STREAM_LABEL);
        mediaStream.addTrack(factory.createAudioTrack(PeerConnectionUtilities.AUDIO_TRACK_ID,
                factory.createAudioSource(audioConstraints)));

        peerConnection.addStream(mediaStream);

        if (peerConnectionParameters.isAecDump()) {
            try {
                aecDumpFileDescriptor = ParcelFileDescriptor.open(
                        new File("/sdcard/Download/audio.aecdump"),
                        ParcelFileDescriptor.MODE_READ_WRITE |
                        ParcelFileDescriptor.MODE_CREATE |
                        ParcelFileDescriptor.MODE_TRUNCATE);
            } catch (IOException e) {
                Log.d(LOG_TAG, "Exception: " + e.getMessage() + ", Can not open aecDump file");
            }
        }

        Log.d(LOG_TAG, "Peer connection created!");
    }

    public void closeInternal() {
        Log.d(LOG_TAG, "Closing peer connection.");
        statsTimer.cancel();

        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }

        Log.d(LOG_TAG, "Closing peer connection factory.");
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        options = null;

        Log.d(LOG_TAG, "Closing peer connection done!");
        events.onPeerConnectionClosed();
    }

    private void getStats() {
        if (peerConnection == null || isError) {
            return;
        }

        boolean success = peerConnection.getStats(new StatsObserver() {
            @Override
            public void onComplete(StatsReport[] statsReports) {
                events.onPeerConnectionStatsReady(statsReports);
            }
        }, null);

        if (!success) {
            Log.d(LOG_TAG, "getStats returns false!");
        }
    }

    public void enableStatsEvents(boolean enable, int periodMs) {
        if (enable) {
            try {
                statsTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                getStats();
                            }
                        });
                    }
                }, 0, periodMs);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Can not schedule statistics timer", e);
            }
        } else {
            statsTimer.cancel();
        }
    }

    public void createOffer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null && !isError) {
                    Log.d(LOG_TAG, "PC create offer.");
                    isInitiator = true;
                    peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
                }
            }
        });
    }

    public void createAnswer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null && !isError) {
                    Log.d(LOG_TAG, "Create PC answer.");
                    isInitiator = false;
                    peerConnection.createAnswer(sdpObserver, sdpMediaConstraints);
                }
            }
        });
    }

    public void addRemoteIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection != null && !isError) {
                    if (queuedRemoteCandidates != null) {
                        queuedRemoteCandidates.add(candidate);
                    } else {
                        peerConnection.addIceCandidate(candidate);
                    }
                }
            }
        });
    }

    public void setRemoteDescription(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (peerConnection == null || isError) {
                    return;
                }

                String sdpDescription = sdp.description;

                if (preferIsac) {
                    sdpDescription = preferCodec(sdpDescription, PeerConnectionUtilities.AUDIO_CODEC_ISAC, true);
                }

                if (peerConnectionParameters.getAudioStartBitrate() > 0) {
                    sdpDescription = setStartBitrate(PeerConnectionUtilities.AUDIO_CODEC_OPUS,
                            sdpDescription,
                            peerConnectionParameters.getAudioStartBitrate());
                }
                Log.d(LOG_TAG, "Set remote sdp.");

                SessionDescription sdpRemote = new SessionDescription(sdp.type, sdpDescription);
                peerConnection.setRemoteDescription(sdpObserver, sdpRemote);
            }
        });
    }

    private void reportError(final String errorMessage) {
        Log.d(LOG_TAG, "Peer connection error: " + errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (!isError) {
                    events.onPeerConnectionError(errorMessage);
                    isError = true;
                }
            }
        });
    }

    private static String setStartBitrate(String codec, String sdpDescription, int bitrateKbps) {
        String[] lines = sdpDescription.split("\r\n");
        int rtpmapLineIndex = -1;
        boolean sdpFormatUpdated = false;
        String codecRtpMap = null;

        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);

        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                rtpmapLineIndex = i;
                break;
            }
        }

        if (codecRtpMap == null) {
            Log.w(LOG_TAG, "No rtp map for " + codec + " codec");
            return sdpDescription;
        }

        Log.d(LOG_TAG, "Found " + codec + " rtpmap" + codecRtpMap + " at" + lines[rtpmapLineIndex]);

        regex = "^a=fmtp:" + codecRtpMap + " \\w+=\\d+.*[\r]?$]";
        codecPattern = Pattern.compile(regex);

        for (int i = 0; i < lines.length; i++) {
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                Log.d(LOG_TAG, "Found " + codec + " " + lines[i]);
                lines[i] += "; " + PeerConnectionUtilities.AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
            }
            Log.d(LOG_TAG, "Update remote SDP line: " + lines[i]);
            sdpFormatUpdated = true;
            break;
        }

        StringBuilder newSdpDescription = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            newSdpDescription.append(lines[i]).append("\r\n");
            if (!sdpFormatUpdated && i == rtpmapLineIndex) {
                String bitrateSet = "a=fmtp:" + codecRtpMap + " " +
                        PeerConnectionUtilities.AUDIO_CODEC_PARAM_BITRATE + "=" + (bitrateKbps * 1000);
                Log.d(LOG_TAG, "Add remote SDP line " + bitrateSet);
                newSdpDescription.append(bitrateSet).append("\r\n");
            }
        }

        return newSdpDescription.toString();
    }

    private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        String[] lines = sdpDescription.split("\r\n");
        int lineIndex = -1;
        String codecRtpMap = null;

        String regex = "^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$";
        Pattern codecPattern = Pattern.compile(regex);

        String mediaDescription = "m=audio";

        for (int i = 0; (i < lines.length) && (lineIndex == -1 || codecRtpMap == null); i++) {
            if (lines[i].startsWith(mediaDescription)) {
                lineIndex = i;
                continue;
            }
            Matcher codecMatcher = codecPattern.matcher(lines[i]);
            if (codecMatcher.matches()) {
                codecRtpMap = codecMatcher.group(1);
                continue;
            }
        }

        if (lineIndex == -1) {
            Log.w(LOG_TAG, "No " + mediaDescription + " line, so can not prefer " + codec);
            return sdpDescription;
        }
        if (codecRtpMap == null) {
            Log.w(LOG_TAG, "No rtpMap for " + codec);
            return sdpDescription;
        }
        Log.d(LOG_TAG, "Found " + codec + " rtpmap " + codecRtpMap + " ,prefer at " + lines[lineIndex]);

        String[] origMLineParts = lines[lineIndex].split(" ");
        if (origMLineParts.length > 3) {
            StringBuilder newLine = new StringBuilder();
            int originPartIndex = 0;

            newLine.append(origMLineParts[originPartIndex++]).append(" ");
            newLine.append(origMLineParts[originPartIndex++]).append(" ");
            newLine.append(origMLineParts[originPartIndex++]).append(" ");
            newLine.append(codecRtpMap);

            for (; originPartIndex < origMLineParts.length; originPartIndex++) {
                if (!origMLineParts[originPartIndex].equals(codecRtpMap)) {
                    newLine.append(" ").append(origMLineParts[originPartIndex]);
                }
            }
            lines[lineIndex] = newLine.toString();
            Log.d(LOG_TAG, "Change media description: " + lines[lineIndex]);
        } else {
            Log.d(LOG_TAG, "Wrong SDP media description format: " + lines[lineIndex]);
        }

        StringBuilder newSdpDescription = new StringBuilder();
        for (String line : lines) {
            newSdpDescription.append(line).append("\r\n");
        }

        return newSdpDescription.toString();
    }

    private void drainCandidates() {
        if (queuedRemoteCandidates != null) {
            Log.d(LOG_TAG, "Add " + queuedRemoteCandidates.size() + " remotes candidates");
            for (IceCandidate candidate : queuedRemoteCandidates) {
                peerConnection.addIceCandidate(candidate);
            }
            queuedRemoteCandidates = null;
        }
    }

    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(LOG_TAG, "SignalingState: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState iceConnectionState) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_TAG, "IceConnectionState: " + iceConnectionState);
                    if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                        events.onIceConnected();
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        events.onIceDisconnected();
                    } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                        reportError(PeerConnectionUtilities.ICE_CONNECTION_FAILED);
                    }
                }
            });
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d(LOG_TAG, "IceGatheringState" + iceGatheringState);
        }

        @Override
        public void onIceCandidate(final IceCandidate iceCandidate) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    events.onIceCandidate(iceCandidate);
                }
            });
        }

        @Override
        public void onAddStream(final MediaStream mediaStream) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection == null && isError) {
                        return;
                    }
                    if (mediaStream.audioTracks.size() > 1) {
                        reportError(PeerConnectionUtilities.REPORT_ERROR + mediaStream);
                        return;
                    }
                }
            });
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            reportError("WebRTC doesn't use data channels, but got: " + dataChannel.label() + " anyway!");
        }

        @Override
        public void onRenegotiationNeeded() {

        }
    }

    private class SDPObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            if (localSdp != null) {
                reportError("Multiple SDP create.");
                return;
            }

            String sdpDescription = sessionDescription.description;
            if (preferIsac) {
                sdpDescription = preferCodec(sdpDescription, PeerConnectionUtilities.AUDIO_CODEC_ISAC, true);
            }

            final SessionDescription sdp = new SessionDescription(sessionDescription.type, sdpDescription);
            localSdp = sdp;

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection != null && !isError) {
                        Log.d(LOG_TAG, "Set local sdp from " + sdp.type);
                        peerConnection.setLocalDescription(sdpObserver, sdp);
                    }
                }
            });
        }

        @Override
        public void onSetSuccess() {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (peerConnection == null || isError) {
                        return;
                    }

                    if (isInitiator) {
                        if (peerConnection.getRemoteDescription() == null) {
                            Log.d(LOG_TAG, "Local sdp set successfully!");
                            events.onLocalDescription(localSdp);
                        } else {
                            Log.d(LOG_TAG, "Remote SDP set succussfully!");
                            drainCandidates();
                        }
                    } else {
                        if (peerConnection.getLocalDescription() == null) {
                            Log.d(LOG_TAG, "Local sdp set successfully!");
                            events.onLocalDescription(localSdp);
                            drainCandidates();
                        } else {
                            Log.d(LOG_TAG, "Remote SDP set succussfully!");
                        }
                    }
                }
            });
        }

        @Override
        public void onCreateFailure(String s) {
            reportError("createSDP error: " + s);
        }

        @Override
        public void onSetFailure(String s) {
            reportError("setSDP error: " + s);
        }
    }
}
