package com.wombat.aws_s3;

import static com.amazonaws.services.s3.internal.Constants.KB;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
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
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkConnectionType;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtilityOptions;
import com.amazonaws.regions.Region;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.Md5Utils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private Context applicationContext;
    private TransferUtility transferUtility;
    public static String bucket;
    public static String TAG = "AwsS3Plugin";
    // 从暂停到恢复上传的操作，在监听状态变化中会返回一次paused状态，根据resumeOp这个flag来拦截，避免界面返回暂停/恢复变化
    private final List<TaskData> taskDataList = new ArrayList<>();
    private final UploadListener uploadListener = new UploadListener();

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel =
                new MethodChannel(binding.getBinaryMessenger(), "com.wombat/aws_s3_plugin");

        methodChannel.setMethodCallHandler(this);
        // 设置日志
        LogFactory.setLevel(LogFactory.Level.OFF);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        methodChannel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        Activity activity = binding.getActivity();
        applicationContext = activity.getApplicationContext();
        TransferNetworkLossHandler.getInstance(applicationContext);
        applicationContext.registerReceiver(TransferNetworkLossHandler.getInstance(applicationContext), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        applicationContext.startService(new Intent(applicationContext, TransferService.class));
    }

    @Override
    public void onDetachedFromActivity() {
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
                String finalMd = md5;
                new Handler(Looper.getMainLooper()).post(() -> result.success(finalMd));
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
                .withConnectionTimeout(60 * 1000)
                .withSocketTimeout(60 * 1000)
//                .withCurlLogging(true)
                .withMaxConnections(1)
                .withRetryPolicy(new RetryPolicy(null, null, 1, false));
        AmazonS3Client s3Client = new AmazonS3Client(credentialsProvider, Region.getRegion(regionString), clientConfiguration);
        s3Client.setEndpoint(Objects.requireNonNull(endpoint));
        s3Client.setNotificationThreshold(356 * KB);

        transferUtility = TransferUtility.builder()
                .context(applicationContext)
                .defaultBucket(bucket)
                .s3Client(s3Client)
                .transferUtilityOptions(new TransferUtilityOptions(1, TransferNetworkConnectionType.ANY))
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
            Log.d(TAG, "不合法Number");
        }
        // 判断是否有id = taskId, 状态是暂停/取消/失败状态的任务
        TaskData taskData = null;
        for (TaskData task : taskDataList) {
            if (task.getUuid().equals(uuid)) {
                taskData = task;
                break;
            }
        }

        if (taskData == null) {
            TransferObserver observer = transferUtility.getTransferById(taskId);
            if (observer != null) {
                observer.setTransferListener(uploadListener);
                taskData = new TaskData(uuid, observer);
                taskDataList.add(taskData);
            }
        }

        if (taskData != null) {
            Log.d(TAG, "有旧上传，进行恢复或重试: uuid = " + uuid + ", id = " + taskId + ", 状态: " + taskData.getObserver().getState());
            transferUtility.resume(taskId);
            result.success(taskId);
            return;
        }

        // 新上传
        newUpload(fileName, filePath, objectKey, uuid, result);

    }

    private void newUpload(String fileName, String filePath, String objectKey, String uuid, MethodChannel.Result result) {
        Log.d(TAG, "新上传：objectKey=" + objectKey + ", uuid=" + uuid);
        // 添加文件名 + 文件md5 + 文件类型 + 文件大小
        ObjectMetadata metadata = new ObjectMetadata();
        // 文件名
        try {
            metadata.addUserMetadata("file-name", URLEncoder.encode(fileName, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        TransferObserver observer = transferUtility.upload(objectKey, new File(filePath), metadata);
        observer.setTransferListener(uploadListener);
        taskDataList.add(new TaskData(uuid, observer));
        result.success(observer.getId());
    }

    private void pause(MethodCall call, MethodChannel.Result result) {
        if (transferUtility == null) {
            Log.d(TAG, "transferUtility 还没有初始化");
            result.success(null);
            return;
        }

        String uuid = call.argument("uuid");
        int taskId = 0;
        try {
            taskId = Integer.parseInt(Objects.requireNonNull(call.argument("taskId")));
        } catch (Exception ex) {
            Log.d(TAG, "不合法Number");
        }

        TaskData taskData = getTaskDataByUuid(uuid);
        TransferObserver observer = taskData != null ? taskData.observer : transferUtility.getTransferById(taskId);

        if (observer != null) {
            boolean pauseResult = transferUtility.pause(taskId);
            Log.d(TAG, "uuid: " + uuid + ", id: " + taskId + ", 暂停结果: " + pauseResult);
            // taskData为null表示应用首次启动的暂停，直接返回表示暂停
            result.success(taskData == null ? taskId : -1);
        } else {
            result.success(null);
        }
    }

    private void delete(MethodCall call, MethodChannel.Result result) {
        if (transferUtility == null) {
            Log.d(TAG, "transferUtility 还没有初始化");
            result.success(null);
            return;
        }

        int taskId = 0;
        String uuid = call.argument("uuid");
        try {
            taskId = Integer.parseInt(Objects.requireNonNull(call.argument("taskId")));
        } catch (Exception ex) {
            Log.d(TAG, "不合法Number");
        }

        TaskData taskData = getTaskDataByUuid(uuid);
        TransferObserver observer = taskData != null ? taskData.getObserver() : transferUtility.getTransferById(taskId);
        if (observer != null) {
            boolean deleteResult = transferUtility.deleteTransferRecord(taskId);
            Log.d(TAG, "uuid: " + uuid + ", id: " + taskId + ", 删除结果: " + deleteResult);
            result.success(taskId);
            new Handler(Looper.getMainLooper()).post(() -> methodChannel.invokeMethod("upload_fail", new HashMap<String, Object>() {{
                put("uuid", uuid);
                put("error", "delete task");
                put("canceled", true);
                put("delete", true);
            }}));
        } else {
            result.success(null);
        }
    }

    TaskData getTaskDataById(int id) {
        for (TaskData taskData : taskDataList) {
            if (taskData.observer.getId() == id) {
                return taskData;
            }
        }
        return null;
    }

    TaskData getTaskDataByUuid(String uuid) {
        for (TaskData taskData : taskDataList) {
            if (taskData.getUuid().equals(uuid)) {
                return taskData;
            }
        }
        return null;
    }


    class UploadListener implements TransferListener {
        @Override
        public void onStateChanged(int id, TransferState state) {
            Log.d(TAG, "上传状态变化: id = " + id + ", 状态: " + state.name());
            TaskData taskData = getTaskDataById(id);
            HashMap<String, Object> result = new HashMap<String, Object>() {{
                put("uuid", taskData != null ? taskData.getUuid() : "");
            }};
            String invokeMethod = null;
            if (state == TransferState.WAITING_FOR_NETWORK && !TransferNetworkLossHandler.getInstance(applicationContext).isNetworkConnected()) {
                invokeMethod = "upload_fail";
                result.put("error", "network off");
                result.put("canceled", false);
                result.put("delete", true);
                Log.d(TAG, "没有网络，把任务删除了重新上传");
                transferUtility.deleteTransferRecord(id);
                taskDataList.remove(getTaskDataById(id));
            } else if (state == TransferState.PAUSED) {
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
            new Handler(Looper.getMainLooper()).post(() -> methodChannel.invokeMethod(finalInvokeMethod, result));
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            TransferObserver observer = transferUtility.getTransferById(id);
            TransferState state = null;
            if (observer != null) {
                state = observer.getState();
            }

            Log.d(TAG, "上传进度id: " + id + ", " + bytesCurrent + "/" + bytesTotal + ", 状态: " + state);
            if (state != TransferState.IN_PROGRESS) {
                Log.d(TAG, "上传有进度变化，但是状态不是IN_PROGRESS, 所以取消告知Flutter端");
                return;
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                TaskData taskData = getTaskDataById(id);
                methodChannel.invokeMethod("upload_progress", new HashMap<String, Object>() {{
                    put("uuid", taskData != null ? taskData.getUuid() : "");
                    put("totalBytesSent", bytesCurrent);
                    put("totalBytesExpectedToSend", bytesTotal);
                }});
            });

        }

        @Override
        public void onError(int id, Exception ex) {
            boolean timeout = false;
            if (ex.getMessage() != null) {
                String errorMessage = ex.getMessage().toLowerCase();
                timeout = errorMessage.contains("timeout") || errorMessage.contains("timed out");
            }

            Log.d(TAG, "上传出错了: " + ex.getMessage() + ", 是否超时: " + timeout);

            String finalInfo = ex.getMessage();
            TaskData taskData = getTaskDataById(id);
            boolean finalTimeout = timeout;
            new Handler(Looper.getMainLooper()).post(() -> methodChannel.invokeMethod("upload_fail", new HashMap<String, Object>() {{
                put("uuid", taskData != null ? taskData.getUuid() : "");
                put("error", finalInfo);
                put("canceled", false);
                put("delete", finalTimeout);
            }}));

            if (timeout) {
                Log.d(TAG, "超时请求，把任务清除了。应该很大概率是上次上传Multipart不正常关闭导致无法再次上传");
                transferUtility.deleteTransferRecord(id);
                taskDataList.remove(getTaskDataById(id));
            }

        }
    }

    static class TaskData {
        private final String uuid;
        private final TransferObserver observer;

        TaskData(String uuid, TransferObserver observer) {
            this.uuid = uuid;
            this.observer = observer;
        }

        public String getUuid() {
            return this.uuid;
        }

        public TransferObserver getObserver() {
            return observer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TaskData taskData = (TaskData) o;
            return uuid.equals(taskData.uuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid);
        }
    }

}