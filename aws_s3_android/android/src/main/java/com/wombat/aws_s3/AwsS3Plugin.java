package com.wombat.aws_s3;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.CognitoCredentialsProvider;
import com.amazonaws.auth.STSSessionCredentialsProvider;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtilityOptions;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.Md5Utils;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.StandardMessageCodec;

/**
 * AwsS3AndroidPlugin
 */
public class AwsS3Plugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private MethodChannel methodChannel;
    private Activity activity;
    private TransferUtility transferUtility;
    private String rootRecordDirectory;
    public static String bucket;
    String uploadId;
    public static String TAG = "AwsS3Plugin";

    private final HashMap<String, TransferObserver> uploadTasks = new HashMap<>();
    private List<TaskData> taskList = new ArrayList<>();

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel =
                new MethodChannel(binding.getBinaryMessenger(), "com.wombat/aws_s3_plugin");

        methodChannel.setMethodCallHandler(this);
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
            case "calculate_md5":
                calculateMd5(call, result);
                break;
            case "initialize":
                initialize(call, result);
                break;
            case "upload":
                upload(call, result);
                break;
            case "cancel_upload":
                cancelUploadMethodCall(call, result);
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


//        ClientConfiguration config = new ClientConfiguration();
//        config.setConnectionTimeout(20 * 1000);
//        config.setSocketTimeout(20 * 1000);
//        config.setMaxConcurrentRequest(1);
//        config.setMaxErrorRetry(Integer.MAX_VALUE);

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

        AmazonS3Client sS3Client = new AmazonS3Client(credentialsProvider, Region.getRegion(regionString));
        sS3Client.setEndpoint(endpoint);

        transferUtility = TransferUtility.builder()
                .context(activity.getApplicationContext())
                .defaultBucket(bucket)
                .s3Client(sS3Client)
                .build();

        rootRecordDirectory = activity.getFilesDir().getAbsolutePath() + "/oatos/oss_record/";
        result.success(endpoint);
    }

    private void upload(MethodCall call, MethodChannel.Result result) {
        String fileName = call.argument("fileName");
        String filePath = call.argument("filePath");
        String objectKey = call.argument("objectKey");
        String uuid = call.argument("uuid");

        if (uploadTasks.containsKey(uuid)) {
            int sameUuid = 0;
            for (TaskData taskData : taskList) {
                if (taskData.uuid.equals(uuid)) {
                    Log.d("相同" + (sameUuid++), "uuid = " + taskData.uuid + ", 状态是否取消: " + (taskData.task.getState() == TransferState.CANCELED));
                }
            }
        }

        // 添加文件名 + 文件md5 + 文件类型 + 文件大小
        ObjectMetadata metadata = new ObjectMetadata();
        // 文件名
        try {
            metadata.addUserMetadata("file-name", URLEncoder.encode(fileName, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        TransferObserver task = transferUtility.upload(objectKey, new File(filePath), metadata);
        task.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (state == TransferState.COMPLETED) {
                    activity.runOnUiThread(() -> {
                        methodChannel.invokeMethod("upload_success", new HashMap<String, Object>() {{
                            put("uuid", uuid);
                            put("result", "上传成功能");
                        }});
                        uploadTasks.remove(uuid);
                    });
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                Log.d("上传id", "" + uploadId);
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
                    Boolean finalCanceled = true;
                    activity.runOnUiThread(() -> {
                        methodChannel.invokeMethod("upload_fail", new HashMap<String, Object>() {{
                            put("uuid", uuid);
                            put("error", finalInfo);
                            put("canceled", finalCanceled);
                        }});
                        uploadTasks.remove(uuid);
                    });
                }
            }
        });


        uploadTasks.put(uuid, task);
        taskList.add(new TaskData(uuid, task));
        result.success(uuid);
    }

    private void cancelUploadMethodCall(MethodCall call, MethodChannel.Result result) {
        String uuid = call.argument("uuid");
        boolean delete = Boolean.TRUE.equals(call.argument("delete"));
        if (delete) {
            // 删除断点记录文件
//            FileUtil.Companion.deleteFile(getRecordDir(uuid));
        }
        TransferObserver task = uploadTasks.get(uuid);//uploadTasks.remove(uuid);
        if (task != null) {
            transferUtility.cancel(task.getId());
            result.success(uuid);
        } else {
            result.success(null);
        }
    }

    private File getRecordDir(String uuid) {
        return new File(rootRecordDirectory + File.separator + uuid);
    }
}

class TaskData {
    final String uuid;
    final TransferObserver task;

    TaskData(String uuid, TransferObserver task) {
        this.uuid = uuid;
        this.task = task;
    }
}