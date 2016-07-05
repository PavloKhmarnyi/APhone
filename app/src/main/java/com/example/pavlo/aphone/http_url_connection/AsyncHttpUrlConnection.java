package com.example.pavlo.aphone.http_url_connection;

import android.os.AsyncTask;

import com.example.pavlo.aphone.interfaces.AsyncHttpEvents;
import com.example.pavlo.aphone.util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by pavlo on 23.06.16.
 */
public class AsyncHttpUrlConnection {

    private final String method;
    private final String url;
    private final String message;

    private final AsyncHttpEvents events;

    private String contentType;
    private String response;

    private int responseCode;

    public AsyncHttpUrlConnection(String method, String url, String message, AsyncHttpEvents events) {
        this.method = method;
        this.url = url;
        this.message = message;
        this.events = events;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void send() {
        Runnable runHttp = new Runnable() {
            @Override
            public void run() {
                sendHttpMessage();
            }
        };
        new Thread(runHttp).start();
    }

    public void sendHttpMessage() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            byte[] postData= new byte[0];
            if (message != null) {
                postData = message.getBytes("UTF-8");
            }
            connection.setRequestMethod(method);
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setConnectTimeout(Config.HTTP_TIMEOUT_MS);
            connection.setReadTimeout(Config.HTTP_TIMEOUT_MS);
            connection.addRequestProperty("origin", Config.ROOM_URL);

            boolean doOutput = false;

            if (method.equals("POST")) {
                doOutput = true;
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(postData.length);
            }

            if (contentType == null) {
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            } else {
                connection.setRequestProperty("Content-Type", contentType);
            }

            if (doOutput && postData.length > 0) {
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(postData);
                outputStream.close();
            }

            responseCode = connection.getResponseCode();

            if (responseCode != 200) {
                events.onHttpError("Non-200 response to " + method + " to Url: " + url);
                connection.disconnect();
                return;
            }

            InputStream inputStream = connection.getInputStream();
            response = drainStream(inputStream);
            inputStream.close();
            connection.disconnect();
            events.onHttpComplete(response);
        } catch (SocketTimeoutException e) {
            events.onHttpError("Http " + method + " to " + url + " timeout");
        } catch (IOException e) {
            events.onHttpError("HTTP " + method + " to " + url + " error: "
                    + e.getMessage());
        }
    }

    private static String drainStream(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");

        return scanner.hasNext() ? scanner.next() : "";
    }
}
