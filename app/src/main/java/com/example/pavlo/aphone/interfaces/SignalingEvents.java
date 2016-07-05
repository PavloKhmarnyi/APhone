package com.example.pavlo.aphone.interfaces;

import com.example.pavlo.aphone.parameters.SignalingParameters;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Created by pavlo on 22.06.16.
 */
public interface SignalingEvents {

    public void onConnectedToRoom(final SignalingParameters parameters);

    public void onRemoteDescription(final SessionDescription sdp);

    public void onRemoteIceCandidate(final IceCandidate candidate);

    public void onChannelClose();

    public void onChannelError(final String description);
}
