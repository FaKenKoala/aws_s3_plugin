import 'dart:async';

import 'package:aws_s3_platform_interface/aws_s3_platform_interface.dart';

Future<String> calculateMd5(String filePath) {
  return AwsS3Platform.instance.calculateMd5(filePath);
}

Future initialize(
    String endpoint, String bucket, String region, Credentials credentials) {
  return AwsS3Platform.instance
      .initialize(endpoint, bucket, region, credentials);
}

Future upload(String fileName, String filePath, String objectKey, String uuid,
    String taskId) {
  return AwsS3Platform.instance
      .upload(fileName, filePath, objectKey, uuid, taskId);
}

Future pause(String uuid, String taskId) {
  return AwsS3Platform.instance.pause(uuid, taskId);
}

Future delete(String uuid, String taskId) {
  return AwsS3Platform.instance.delete(uuid, taskId);
}

Future<String?> getTempFilePath(String filePath) {
  return AwsS3Platform.instance.getTempFilePath(filePath);
}

String addUploadMethodCallListener(UploadMethodCallListener listener) {
  return AwsS3Platform.instance.addUploadMethodCallListener(listener);
}

void removeUploadMethodCallListener({String? id, bool removeAll = false}) {
  return AwsS3Platform.instance
      .removeUploadMethodCallListener(id: id, removeAll: removeAll);
}
