package com.wombat.aws_s3;

import static com.wombat.aws_s3.AwsS3Plugin.TAG;

import android.util.Log;

import androidx.annotation.NonNull;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.util.IOUtils;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AuthCredentialsProvider implements AWSCredentialsProvider {

    private String mAuthServerUrl;
    private String mAuthorization;

    private volatile CustomSessionCredentials credentials;
    private static final Object lock = new Object();

    public AuthCredentialsProvider(String authServerUrl, String authorization) {
        this.mAuthServerUrl = authServerUrl;
        this.mAuthorization = authorization;
        credentials = null;
    }

    public void setAuthServerUrl(String authServerUrl) {
        this.mAuthServerUrl = authServerUrl;
        credentials = null;
    }

    public void setAuthorization(String authorization) {
        this.mAuthorization = authorization;
        credentials = null;
    }

    private boolean validCredentials() {
        return credentials != null && DateUtil.getFixedSkewedTimeMillis() / 1000 < credentials.getExpiration() - 5 * 60;
    }

    @Override
    public AWSCredentials getCredentials() {
        synchronized (lock) {
            if (!validCredentials()) {
                if (credentials != null) {
                    Log.d(TAG, "token过期了! current time: " + DateUtil.getFixedSkewedTimeMillis() / 1000 + " token expired: " + credentials.getExpiration());
                }
                refresh();
            }
        }
        return credentials;
    }

    @Override
    public void refresh() {
        Log.d(TAG, "不同part的CredentialsProvider: " + this.hashCode());
        try {
            URL stsUrl = new URL(mAuthServerUrl);
            HttpURLConnection conn = (HttpURLConnection) stsUrl.openConnection();
            conn.setRequestProperty("Authorization", mAuthorization);
            conn.setConnectTimeout(10 * 1000);
            conn.setReadTimeout(10 * 1000);
            InputStream input = conn.getInputStream();

            String authData = IOUtils.toString(input);

            JSONObject jsonObjOut = new JSONObject(authData);
            String statusCode = jsonObjOut.getString("statusCode");
            if (statusCode.equals("OK")) {
                Log.d(TAG, "刷新token成功: " + authData);
                JSONObject jsonObj = jsonObjOut.getJSONObject("data");
                String ak = jsonObj.getString("accessKey");
                String sk = jsonObj.getString("secretKey");
                String token = jsonObj.getString("sessionToken");
                long expiration = jsonObj.getLong("expiration");
                String bucket = jsonObj.getString("bucket");
                String region = jsonObj.getString("region");
                String endpoint = jsonObj.getString("endpoint");
                credentials = new CustomSessionCredentials(ak, sk, token, expiration / 1000);
            } else {
                String errorCode = jsonObjOut.getString("ErrorCode");
                String errorMessage = jsonObjOut.getString("ErrorMessage");
                Log.d(TAG, "获取token出错Inner: ErrorCode: " + errorCode + "| ErrorMessage: " + errorMessage);
                throw new Exception("ErrorCode: " + errorCode + "| ErrorMessage: " + errorMessage);
            }
        } catch (Exception e) {
            Log.d(TAG, "获取token出错Outer: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}

class CustomSessionCredentials extends BasicSessionCredentials {

    private long expiration;

    public CustomSessionCredentials(String awsAccessKey, String awsSecretKey, String sessionToken, long expiration) {
        super(awsAccessKey, awsSecretKey, sessionToken);
        this.expiration = expiration;
    }

    public long getExpiration() {
        return expiration;
    }

    @NonNull
    @Override
    public String toString() {
        return "OSSFederationToken [tempAk=" + getAWSAccessKeyId() + ", tempSk=" + getAWSSecretKey() + ", securityToken="
                + getSessionToken() + ", expiration=" + expiration + "]";
    }
}

