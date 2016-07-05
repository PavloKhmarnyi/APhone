package com.example.pavlo.aphone.parameters;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

/**
 * Created by pavlo on 22.06.16.
 */
public class SignalingParameters {

    private List<PeerConnection.IceServer> iceServers;
    private boolean initiator;
    private String clientId;
    private String wssUrl;
    private String wssPostUrl;
    private SessionDescription offerSdp;
    private List<IceCandidate> iceCandidates;

    private SignalingParameters() {

    }

    public List<PeerConnection.IceServer> getIceServers() {
        return iceServers;
    }

    public boolean isInitiator() {
        return initiator;
    }

    public String getClientId() {
        return clientId;
    }

    public String getWssUrl() {
        return wssUrl;
    }

    public String getWssPostUrl() {
        return wssPostUrl;
    }

    public SessionDescription getOfferSdp() {
        return offerSdp;
    }

    public List<IceCandidate> getIceCandidates() {
        return iceCandidates;
    }

    public static Builder newBuilder() {
        return new SignalingParameters().new Builder();
    }

    public class Builder {

        private Builder() {

        }

        public Builder setIceServers(List<PeerConnection.IceServer> iceServers) {
            SignalingParameters.this.iceServers = iceServers;

            return this;
        }

        public Builder setInitiator(boolean initiator) {
            SignalingParameters.this.initiator = initiator;

            return this;
        }

        public Builder setClientId(String clientId) {
            SignalingParameters.this.clientId = clientId;

            return this;
        }

        public Builder setWssUrl(String wssUrl) {
            SignalingParameters.this.wssUrl = wssUrl;

            return this;
        }

        public Builder setWssPostUrl(String wssPostUrl) {
            SignalingParameters.this.wssPostUrl = wssPostUrl;

            return this;
        }

        public Builder setOfferSdp(SessionDescription offerSdp) {
            SignalingParameters.this.offerSdp = offerSdp;

            return this;
        }

        public Builder setIceCandidates(List<IceCandidate> iceCandidates) {
            SignalingParameters.this.iceCandidates = iceCandidates;

            return this;
        }

        public SignalingParameters biuld() {
            return SignalingParameters.this;
        }
    }
}
