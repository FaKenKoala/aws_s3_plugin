package com.wombat.aws_s3;

import static com.amazonaws.services.s3.internal.Constants.MAXIMUM_UPLOAD_PARTS;
import static com.amazonaws.services.s3.internal.Constants.MB;

import android.content.ContentValues;
import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferDBUtil;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferStatusUpdater;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferThreadPool;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtilityOptions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class CustomTransferUtility {
    private InitiateMultipartUploadResult initResponse;
    private long partSize = 5 * MB;
    private final File file;
    private final String objectKey;
    private final String uuid;
    private final long contentLength;
    private final ObjectMetadata metadata = new ObjectMetadata();
    private final int taskId;
    private long filePosition = 0;

    private boolean pauseOp = false;
    List<PartETag> partETags = new ArrayList<>();
    int partNumber = 0;

    private final AmazonS3 s3;
    private final String defaultBucket;
    private final TransferUtilityOptions transferUtilityOptions;
    private TransferDBUtil dbUtil;

    public CustomTransferUtility(AmazonS3 s3, Context context, String defaultBucket, TransferUtilityOptions tuOptions) {
        this.s3 = s3;
        this.defaultBucket = defaultBucket;
        this.transferUtilityOptions = tuOptions;
        this.dbUtil = new TransferDBUtil(context.getApplicationContext());
        this.updater = TransferStatusUpdater.getInstance(context.getApplicationContext());
        TransferThreadPool.init(this.transferUtilityOptions.getTransferThreadPoolSize());
        this.connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void pause() {
        this.pauseOp = true;
    }

    public void resume() {
        if (partNumber <= 0) {
            initiateMultipartUpload();
        } else {
            uploadMultipart();
        }
    }

    int upload() {
        initiateMultipartUpload();
        return taskId;
    }

    public CustomTransferUtility(String fileName, String filePath, String objectKey, String uuid) {
        Log.i(TAG, "新上传：objectKey=" + objectKey + ", uuid=" + uuid);
        this.objectKey = objectKey;
        this.uuid = uuid;
        try {
            metadata.addUserMetadata("file-name", URLEncoder.encode(fileName, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        file = new File(filePath);
        contentLength = file.length();
        taskId = new Random().nextInt();
    }

    private void initiateMultipartUpload() {
        new Thread(() -> {
            if (initResponse == null) {
                try {
                    // Initiate the multipart upload.
                    InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucket, objectKey, metadata);
                    initResponse = s3Client.initiateMultipartUpload(initRequest);
                } catch (AmazonServiceException e) {
                    Log.i(TAG, "上传错误11 AmazonServiceException: " + e.getMessage());
                    return;
                } catch (Exception e) {
                    Log.i(TAG, "上传错误12: " + e.getMessage());
                    return;
                }
            }

            uploadMultipart();
        }).start();
    }

    private void uploadMultipart() {
        new Thread(() -> {
            try {
                for (int i = partNumber; filePosition < contentLength; i++) {
                    if (pauseOp) {
                        pauseOp = false;
                        Log.i("上传part", "接收到暂停操作, partNumber = " + i);
                        notifyFail(uuid, true);
                        return;
                    }
                    partNumber = i;
                    // Because the last part could be less than 5 MB, adjust the part size as needed.
                    partSize = Math.min(partSize, (contentLength - filePosition));

                    // Create the request to upload a part.
                    UploadPartRequest uploadRequest = new UploadPartRequest()
                            .withBucketName(bucket)
                            .withKey(objectKey)
                            .withUploadId(initResponse.getUploadId())
                            .withPartNumber(partNumber)
                            .withFileOffset(filePosition)
                            .withFile(file)
                            .withPartSize(partSize);
                    // Upload the part and add the response's ETag to our list.
                    UploadPartResult uploadResult = s3Client.uploadPart(uploadRequest);

                    partETags.add(uploadResult.getPartETag());

                    Log.i("上传part", "上传第" + i + "块, 大小: " + partSize + ", 结果: " + uploadResult);
                    filePosition += partSize;
                    notifyProgress(uuid, filePosition, contentLength);

                }
            } catch (Exception e) {
                if (e instanceof AmazonServiceException) {
                    Log.i(TAG, "上传错误21 AmazonServiceException: " + e.getMessage());
                } else {
                    Log.i(TAG, "上传错误22: " + e.getMessage());
                }
                notifyFail(uuid, false);
                return;
            }
            completeMultipartUpload();
        }).start();
    }

    private void completeMultipartUpload() {
        new Thread(() -> {
            try {
                // Complete the multipart upload.
                CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(bucket, objectKey,
                        initResponse.getUploadId(), partETags);
                CompleteMultipartUploadResult completeMultipartUploadResult = s3Client.completeMultipartUpload(compRequest);
                Log.i("上传part", "结束multipart上传结果: " + completeMultipartUploadResult);
                notifySuccess(uuid);
            } catch (Exception e) {
                if (e instanceof AmazonServiceException) {
                    Log.i(TAG, "上传错误31AmazonServiceException: " + e.getMessage());
                } else {
                    Log.i(TAG, "上传错误32: " + e.getMessage());
                }
                notifyFail(uuid, false);
            }
        }).start();
    }


    private int createMultipartUploadRecords(String bucket, String key, File file, ObjectMetadata metadata,
                                             CannedAccessControlList cannedAcl) {
        long remainingLenth = file.length();
        double partSize = (double) remainingLenth / (double) MAXIMUM_UPLOAD_PARTS;
        partSize = Math.ceil(partSize);
        final long optimalPartSize = (long) Math.max(partSize, 5 * MB);
        long fileOffset = 0;
        int partNumber = 1;

        // the number of parts
        final int partCount = (int) Math.ceil((double) remainingLenth / (double) optimalPartSize);

        /*
         * the size of valuesArray is partCount + 1, one for a multipart upload summary,
         * others are actual parts to be uploaded
         */
        final ContentValues[] valuesArray = new ContentValues[partCount + 1];
        valuesArray[0] = dbUtil.generateContentValuesForMultiPartUpload(bucket, key, file, fileOffset, 0, "",
                file.length(), 0, metadata, cannedAcl, transferUtilityOptions);
        for (int i = 1; i < partCount + 1; i++) {
            final long bytesForPart = Math.min(optimalPartSize, remainingLenth);
            valuesArray[i] = dbUtil.generateContentValuesForMultiPartUpload(bucket, key, file, fileOffset, partNumber,
                    "", bytesForPart, remainingLenth - optimalPartSize <= 0 ? 1 : 0, metadata, cannedAcl, transferUtilityOptions);
            fileOffset += optimalPartSize;
            remainingLenth -= optimalPartSize;
            partNumber++;
        }
        return dbUtil.bulkInsertTransferRecords(valuesArray);
    }
}
