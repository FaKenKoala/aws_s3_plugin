import 'dart:async';

import 'package:aws_s3_platform_interface/aws_s3_platform_interface.dart';

Future<String> calculateMd5(String filePath) {
  return AwsS3Platform.instance.calculateMd5(filePath);
}

Future<dynamic> initialize(
    String endpoint, String bucket, String region, Credentials credentials) {
  return AwsS3Platform.instance.initialize(endpoint, bucket, region, credentials);
}

Future<dynamic> upload(
    String fileName, String filePath, String objectKey, String uuid) {
  return AwsS3Platform.instance.upload(fileName, filePath, objectKey, uuid);
}

Future<String?> cancelUpload(String uuid, {bool delete = false}) {
  return AwsS3Platform.instance.cancelUpload(uuid, delete: delete);
}

String addUploadMethodCallListener(UploadMethodCallListener listener) {
  return AwsS3Platform.instance.addUploadMethodCallListener(listener);
}

void removeUploadMethodCallListener({String? id, bool removeAll = false}) {
  return AwsS3Platform.instance
      .removeUploadMethodCallListener(id: id, removeAll: removeAll);
}