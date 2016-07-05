package com.example.pavlo.aphone.util;

/**
 * Created by pavlo on 22.06.16.
 */
public class PeerConnectionUtilities {

    public static final String AUDIO_TRACK_ID = "ARDAMSa0";

    public static final String AUDIO_CODEC_OPUS = "opus";
    public static final String AUDIO_CODEC_ISAC = "ISAC";

    public static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
    public static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    public static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT= "googAutoGainControl";
    public static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT  = "googHighpassFilter";
    public static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    public static final String DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement";

    public static final String OFFER_TO_RECIEVE_AUDIO = "OfferToReceiveAudio";
    public static final String OFFER_TO_RECIEVE_VIDEO = "OfferToReceiveVideo";
    public static final String LOCAL_MEDIA_STREAM_LABEL = "ARDAMS";

    public static final String EXTRA_ROOMID = "org.appspot.apprtc.ROOMID";
    public static final String EXTRA_LOOPBACK = "org.appspot.apprtc.LOOPBACK";
    public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE";
    public static final String EXTRA_AUDIO_BITRATE = "org.appspot.apprtc.AUDIO_BITRATE";
    public static final String EXTRA_AUDIOCODEC = "org.appspot.apprtc.AUDIOCODEC";
    public static final String EXTRA_NOAUDIOPROCESSING_ENABLED = "org.appspot.apprtc.NOAUDIOPROCESSING";
    public static final String EXTRA_AECDUMP_ENABLED = "org.appspot.apprtc.AECDUMP";

    public static final String ICE_CONNECTION_FAILED = "ICE connection failed.";
    public static final String REPORT_ERROR = "Weird-looking stream: ";
}
