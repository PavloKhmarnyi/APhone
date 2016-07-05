package com.example.pavlo.aphone.parameters;

/**
 * Created by pavlo on 22.06.16.
 */
public class PeerConnectionParameters {

    private int audioStartBitrate;
    private String audioCodec;

    private boolean loopback;
    private boolean noAudioProcessing;
    private boolean aecDump;
    private boolean useOpenSLES;

    private PeerConnectionParameters() {

    }

    public boolean isLoopback() {
        return loopback;
    }

    public int getAudioStartBitrate() {
        return audioStartBitrate;
    }

    public String getAudioCodec() {
        return audioCodec;
    }

    public boolean isNoAudioProcessing() {
        return noAudioProcessing;
    }

    public boolean isAecDump() {
        return aecDump;
    }

    public boolean isUseOpenSLES() {
        return useOpenSLES;
    }

    public static Builder newBuilder() {
        return new PeerConnectionParameters().new Builder();
    }

    public class Builder {

        private Builder() {

        }

        public Builder setLoopback(boolean loopback) {
            PeerConnectionParameters.this.loopback = loopback;

            return this;
        }

        public Builder setAudioStartBitrate(int audioStartBitrate) {
            PeerConnectionParameters.this.audioStartBitrate = audioStartBitrate;

            return this;
        }

        public Builder setAudioCodec(String audioCodec) {
            PeerConnectionParameters.this.audioCodec = audioCodec;

            return this;
        }

        public Builder setNoAudioProcessing(boolean noAudioProcessing) {
            PeerConnectionParameters.this.noAudioProcessing = noAudioProcessing;

            return this;
        }

        public Builder setAecDump(boolean aecDump) {
            PeerConnectionParameters.this.aecDump = aecDump;

            return this;
        }

        public Builder setUseOpenSLES(boolean useOpenSLES) {
            PeerConnectionParameters.this.useOpenSLES = useOpenSLES;

            return this;
        }

        public PeerConnectionParameters build() {
            return PeerConnectionParameters.this;
        }
    }
}
