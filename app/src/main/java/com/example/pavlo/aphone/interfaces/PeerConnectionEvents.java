package com.example.pavlo.aphone.interfaces;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;

/**
 * Created by pavlo on 22.06.16.
 */
public interface PeerConnectionEvents {

    public void onLocalDescription(final SessionDescription sdp);

    public void onIceCandidate(final IceCandidate candidate);

    public void onIceConnected();

    public void onIceDisconnected();

    public void onPeerConnectionClosed();

    public void onPeerConnectionStatsReady(final StatsReport[] reports);

    public void onPeerConnectionError(final String description);
}
