package com.example.pavlo.aphone.interfaces;

import com.example.pavlo.aphone.parameters.SignalingParameters;

/**
 * Created by pavlo on 22.06.16.
 */
public interface RoomParametersFetcherEvents {

    public void onSignalingParametersReady(final SignalingParameters parameters);

    public void onSignalingParametersError(final String description);
}
