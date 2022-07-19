package com.wombat.aws_s3;

import android.app.Activity;
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
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.Md5Utils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
    private TransferUtility transferUtility;
    public static String bucket;
    public static String TAG = "AwsS3Plugin";

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel =
                new MethodChannel(binding.getBinaryMessenger(), "com.wombat/aws_s3_plugin");

        methodChannel.setMethodCallHandler(this);
        // 设置日志
        LogFactory.setLevel(LogFactory.Level.ALL);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
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
//                .withMaxConnections(5)
                ;
        AmazonS3Client sS3Client = new AmazonS3Client(credentialsProvider, Region.getRegion(regionString), clientConfiguration);
        sS3Client.setEndpoint(endpoint);

        transferUtility = TransferUtility.builder()
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
            taskId = Integer.parseInt((String) Objects.requireNonNull(call.argument("taskId")));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        Log.d(TAG, "开始上传uuid: " + uuid + ", id = " + taskId);

        // 判断是否有id = taskId, 状态是暂停/取消/失败状态的任务
        TransferObserver observer = transferUtility.getTransferById(taskId);
        if (observer != null) {
            TransferState state = observer.getState();
            observer = transferUtility.resume(taskId);
            if (observer != null) {
                Log.d(TAG, "有旧上传，进行恢复或重试: id = " + taskId + ", 状态: " + state + ", 恢复后状态: " + observer.getState());
                observer.setTransferListener(new UploadListener(uuid));
                result.success(taskId);
                return;
            }
        }

        // 新上传
        newUpload(fileName, filePath, objectKey, uuid, result);
    }

    private void newUpload(String fileName, String filePath, String objectKey, String uuid, MethodChannel.Result result) {
        Log.d(TAG, "新上传");
        // 添加文件名 + 文件md5 + 文件类型 + 文件大小
        ObjectMetadata metadata = new ObjectMetadata();
        // 文件名
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
            Log.i(TAG, "transferUtility 还没有初始化");
            result.success(null);
            return;
        }
        int taskId = 0;
        try {
            taskId = Integer.parseInt((String) Objects.requireNonNull(call.argument("taskId")));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        TransferObserver observer = transferUtility.getTransferById(taskId);
        if (observer != null) {
            boolean pauseResult = transferUtility.pause(taskId);
            Log.i(TAG, "taskId: " + taskId + ", 暂停结果: " + pauseResult);
            result.success(taskId);
        } else {
            result.success(null);
        }
    }

    private void delete(MethodCall call, MethodChannel.Result result) {
        if (transferUtility == null) {
            Log.i(TAG, "transferUtility 还没有初始化");
            result.success(null);
            return;
        }
        int taskId = 0;
        String uuid = call.argument("uuid");
        try {
            taskId = Integer.parseInt((String) Objects.requireNonNull(call.argument("taskId")));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        TransferObserver observer = transferUtility.getTransferById(taskId);
        if (observer != null) {
            boolean deleteResult = transferUtility.deleteTransferRecord(taskId);
            Log.i(TAG, "taskId: " + taskId + ", 删除结果: " + deleteResult);
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
            Log.d(TAG, "上传状态变化: id = " + id + ", 状态: " + state.name());
            HashMap<String, Object> result = new HashMap<String, Object>() {{
                put("uuid", uuid);
            }};
            String invokeMethod = null;
            if (state == TransferState.PAUSED) {
                invokeMethod = "upload_fail";
                result.put("error", state.name());
                result.put("canceled", true);
            } else if (state == TransferState.COMPLETED) {
                invokeMethod = "upload_success";
                result.put("result", "上传成功");

            }
            if (invokeMethod == null) {
                return;
            }

            String finalInvokeMethod = invokeMethod;
            activity.runOnUiThread(() -> methodChannel.invokeMethod(finalInvokeMethod, result));
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            TransferState state = transferUtility.getTransferById(id).getState();

            Log.d(TAG, "上传进度id: " + id + ", 状态: " + state);
            if (state != TransferState.IN_PROGRESS) {
                Log.d(TAG, "上传有进度变化，但是状态不是IN_PROGRESS, 所以取消告知Flutter端");
                return;
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
            Log.d(TAG, "上传出错了: " + ex.getMessage());
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

