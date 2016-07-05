package com.example.pavlo.aphone.interfaces;

/**
 * Created by pavlo on 23.06.16.
 */
public interface AsyncHttpEvents {

    public void onHttpError(String errorMessage);

    public void onHttpComplete(String response);
}
