package com.wombat.aws_s3;

import static com.amazonaws.services.s3.internal.Constants.KB;

import android.app.Activity;
import android.content.Intent;
import android.text.format.Time;
import android.util.Log;

import androidx.annotation.NonNull;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.logging.LogFactory;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.CustomTransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.Md5Utils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Objects;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;

/**
 * AwsS3AndroidPlugin
 */
public class AwsS3Plugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private MethodChannel methodChannel;
    private Activity activity;
    private CustomTransferUtility transferUtility;
    public static String bucket;
    public static String TAG = "AwsS3Plugin";
    // ???????????????????????????????????????????????????????????????????????????paused???????????????resumeOp??????flag????????????????????????????????????/????????????
    private boolean resumeOp = false;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel =
                new MethodChannel(binding.getBinaryMessenger(), "com.wombat/aws_s3_plugin");

        methodChannel.setMethodCallHandler(this);
        // ????????????
        LogFactory.setLevel(LogFactory.Level.ALL);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        activity.getApplicationContext().startService(new Intent(activity.getApplicationContext(), TransferService.class));
        TransferNetworkLossHandler.getInstance(activity.getApplicationContext());
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

    }


    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        switch (call.method) {
            case "calculateMd5":
                calculateMd5(call, result);
                break;
            case "initialize":
                initialize(call, result);
                break;
            case "upload":
                upload(call, result);
                break;
            case "pause":
                pause(call, result);
                break;
            case "delete":
                delete(call, result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void calculateMd5(MethodCall call, MethodChannel.Result result) {
        String filePath = call.argument("filePath");
        new Thread(() -> {
            String md5 = "";
            try {
                if (filePath != null) {
                    md5 = BinaryUtils.toHex(Md5Utils.computeMD5Hash(new File(filePath)));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (activity != null) {
                    String finalMd = md5;
                    activity.runOnUiThread(() -> result.success(finalMd));
                }
            }
        }).start();
    }

    private void initialize(MethodCall call, MethodChannel.Result result) {

        String endpoint = call.argument("endpoint");
        String regionString = call.argument("region");
        if (regionString == null) {
            regionString = "us-east-1";
        }
        bucket = call.argument("bucket");
        String accessKeyId = call.argument("accessKeyId");

        AWSCredentialsProvider credentialsProvider;
        if (accessKeyId != null) {
            AWSCredentials credentials;
            String secretKeyId = call.argument("secretKeyId");
            String securityToken = call.argument("securityToken");
            if (securityToken != null) {
                credentials = new BasicSessionCredentials(accessKeyId, secretKeyId, securityToken);
            } else {
                credentials = new BasicAWSCredentials(accessKeyId, secretKeyId);
            }
            credentialsProvider = new StaticCredentialsProvider(credentials);
        } else {
            String authUrl = call.argument("authUrl");
            String authorization = call.argument("authorization");
            credentialsProvider = new AuthCredentialsProvider(authUrl, authorization);
        }

        ClientConfiguration clientConfiguration = new ClientConfiguration()
                .withConnectionTimeout(20 * 1000)
                .withSocketTimeout(20 * 1000)
//                .withCurlLogging(true)
                .withMaxConnections(1)
//                .withMaxErrorRetry(10)
                ;
        AmazonS3Client sS3Client = new AmazonS3Client(credentialsProvider, Region.getRegion(regionString), clientConfiguration);
        sS3Client.setEndpoint(endpoint);
        sS3Client.setNotificationThreshold(356 * KB);

        transferUtility = CustomTransferUtility.builder()
                .context(activity.getApplicationContext())
                .defaultBucket(bucket)
                .s3Client(sS3Client)
                .build();


        result.success(endpoint);
    }

    private void upload(MethodCall call, MethodChannel.Result result) {
        String fileName = call.argument("fileName");
        String filePath = call.argument("filePath");
        String objectKey = call.argument("objectKey");
        String uuid = call.argument("uuid");
        int taskId = 0;
        try {
            taskId = Integer.parseInt(Objects.requireNonNull(call.argument("taskId")));
        } catch (Exception ex) {
            Log.d(TAG, "?????????Number");
        }
        // ???????????????id = taskId, ???????????????/??????/?????????????????????
        TransferObserver observer = transferUtility.getTransferById(taskId);
        if (observer != null) {
            TransferState state = observer.getState();
            observer = transferUtility.resume(taskId);
            if (observer != null) {
                Log.d(TAG, "????????????????????????????????????: id = " + taskId + ", ??????: " + state + ", ???????????????: " + observer.getState());
                resumeOp = true;
                observer.setTransferListener(new UploadListener(uuid));
                result.success(taskId);
                return;
            }
        }

        // ?????????
        newUpload(fileName, filePath, objectKey, uuid, result);
    }

    private void newUpload(String fileName, String filePath, String objectKey, String uuid, MethodChannel.Result result) {
        Log.d(TAG, "????????????objectKey=" + objectKey + ", uuid=" + uuid);
        // ??????????????? + ??????md5 + ???????????? + ????????????
        ObjectMetadata metadata = new ObjectMetadata();
        // ?????????
        try {
            metadata.addUserMetadata("file-name", URLEncoder.encode(fileName, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        TransferObserver task = transferUtility.upload(objectKey, new File(filePath), metadata);
        task.setTransferListener(new UploadListener(uuid));

        result.success(task.getId());
    }


    private void pause(MethodCall call, MethodChannel.Result result) {
        if (transferUtility == null) {
            Log.d(TAG, "transferUtility ??????????????????");
            result.success(null);
            return;
        }
        int taskId = 0;
        try {
            taskId = Integer.parseInt(Objects.requireNonNull(call.argument("taskId")));
        } catch (Exception ex) {
            Log.d(TAG, "?????????Number");
        }
        TransferObserver observer = transferUtility.getTransferById(taskId);
        if (observer != null) {
            boolean pauseResult = transferUtility.pause(taskId);
            Log.d(TAG, "taskId: " + taskId + ", ????????????: " + pauseResult);
            result.success(taskId);
        } else {
            result.success(null);
        }
    }

    private void delete(MethodCall call, MethodChannel.Result result) {
        if (transferUtility == null) {
            Log.d(TAG, "transferUtility ??????????????????");
            result.success(null);
            return;
        }
        int taskId = 0;
        String uuid = call.argument("uuid");
        try {
            taskId = Integer.parseInt(Objects.requireNonNull(call.argument("taskId")));
        } catch (Exception ex) {
            Log.d(TAG, "?????????Number");
        }
        TransferObserver observer = transferUtility.getTransferById(taskId);
        if (observer != null) {
            boolean deleteResult = transferUtility.deleteTransferRecord(taskId);
            Log.d(TAG, "taskId: " + taskId + ", ????????????: " + deleteResult);
            result.success(taskId);
            activity.runOnUiThread(() -> methodChannel.invokeMethod("upload_fail", new HashMap<String, Object>() {{
                put("uuid", uuid);
                put("error", "pause task");
                put("canceled", true);
            }}));
        } else {
            result.success(null);
        }
    }


    class UploadListener implements TransferListener {
        private final String uuid;

        UploadListener(String uuid) {
            this.uuid = uuid;
        }

        @Override
        public void onStateChanged(int id, TransferState state) {
            Log.d(TAG, new Time() + " ??????????????????: id = " + id + ", ??????: " + state.name());
            HashMap<String, Object> result = new HashMap<String, Object>() {{
                put("uuid", uuid);
            }};
            String invokeMethod = null;
            if (state == TransferState.PAUSED) {
                if (!resumeOp) {
                    invokeMethod = "upload_fail";
                    result.put("error", state.name());
                    result.put("canceled", true);
                }
                resumeOp = false;
            } else if (state == TransferState.COMPLETED) {
                invokeMethod = "upload_success";
                result.put("result", "????????????");

            }
            if (invokeMethod == null) {
                return;
            }

            String finalInvokeMethod = invokeMethod;
            activity.runOnUiThread(() -> methodChannel.invokeMethod(finalInvokeMethod, result));
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            TransferObserver observer = transferUtility.getTransferById(id);
            TransferState state = null;
            if (observer != null) {
                state = observer.getState();
            }

            Log.d(TAG, "????????????id: " + id + ", " + bytesCurrent + "/" + bytesTotal + ", ??????: " + state);
            if (state != TransferState.IN_PROGRESS) {
                Log.d(TAG, "??????????????????????????????????????????IN_PROGRESS, ????????????????????????Flutter???");
//                return;
            }
            if (activity != null) {
                activity.runOnUiThread(() -> methodChannel.invokeMethod("upload_progress", new HashMap<String, Object>() {{
                    put("uuid", uuid);
                    put("totalBytesSent", bytesCurrent);
                    put("totalBytesExpectedToSend", bytesTotal);
                }}));
            }
        }

        @Override
        public void onError(int id, Exception ex) {
            Log.d(TAG, "???????????????: " + ex.getMessage());
            if (activity != null) {
                String finalInfo = ex.getMessage();
                activity.runOnUiThread(() -> {
                    methodChannel.invokeMethod("upload_fail", new HashMap<String, Object>() {{
                        put("uuid", uuid);
                        put("error", finalInfo);
                        put("canceled", false);
                    }});
                });
            }
        }
    }

}