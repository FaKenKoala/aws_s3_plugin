import 'package:aws_s3_platform_interface/aws_s3_platform_interface.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

const MethodChannel _channel = MethodChannel('com.wombat/aws_s3_plugin');

class MethodChannelAWSS3 extends AwsS3Platform {
  @visibleForTesting
  MethodChannel get channel => _channel;

  @override
  Future<String> calculateMd5(String filePath) async {
    return (await _channel
        .invokeMethod('calculate_md5', {'filePath': filePath})) as String;
  }

  @override
  initialize(String endpoint, String bucket, Credentials credentials) {
    return _channel.invokeMethod(
        'initialize',
        credentials.toJson()
          ..addAll({
            "endpoint": endpoint,
            "bucket": bucket,
          }));
  }

  @override
  upload(String fileName, String filePath, String objectKey, String uuid) {
    return _channel.invokeMethod('upload', {
      "fileName": fileName,
      "filePath": filePath,
      "objectKey": objectKey,
      "uuid": uuid,
    });
  }

  @override
  Future<String?> cancelUpload(String uuid, {bool delete = false}) {
    return _channel.invokeMethod('cancel_upload', {
      'uuid': uuid,
      'delete': delete,
    });
  }
}
