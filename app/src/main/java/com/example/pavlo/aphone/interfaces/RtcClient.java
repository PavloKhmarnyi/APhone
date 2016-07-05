package com.example.pavlo.aphone.interfaces;

import com.example.pavlo.aphone.parameters.RoomConnectionParameters;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Created by pavlo on 22.06.16.
 */
public interface RtcClient {

    public void connectToRoom(RoomConnectionParameters connectionParameters);

    public void sendOfferSdp(final SessionDescription sdp);

    public void sendAnswerSdp(final SessionDescription sdp);

    public void sendLocalIceCandidate(IceCandidate candidate);

    public void disconnectFromRoom();
}
