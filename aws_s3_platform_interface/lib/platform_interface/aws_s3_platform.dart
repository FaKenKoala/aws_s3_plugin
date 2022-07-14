import 'package:aws_s3_platform_interface/method_channel/method_channel_aws_s3.dart';
import 'package:aws_s3_platform_interface/types/credentials.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class AwsS3Platform extends PlatformInterface {
  AwsS3Platform() : super(token: _token);

  static final Object _token = Object();

  static AwsS3Platform _instance = MethodChannelAWSS3();
  static AwsS3Platform get instance => _instance;

  static set instance(AwsS3Platform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  calculateMd5(String filePath) {
    throw UnimplementedError("calculateMd5() has not been implemented.");
  }

  initialize(
      String endpoint, String bucket, String region, Credentials credentials) {
    throw UnimplementedError('initialize() has not been implemented.');
  }

  Future upload(String fileName, String filePath, String objectKey, String uuid, String taskId) {
    throw UnimplementedError('upload() has not been implemented.');
  }

  Future pause(String uuid, String taskId) {
    throw UnimplementedError('pause() has not been implemented.');
  }

  Future delete(String uuid, String taskId) {
    throw UnimplementedError('delete() has not been implemented.');
  }

  String addUploadMethodCallListener(UploadMethodCallListener listener) {
    throw UnimplementedError(
        'addUploadMethodCallListener() has not been implemented.');
  }

  void removeUploadMethodCallListener({String? id, bool removeAll = false}) {
    throw UnimplementedError(
        'removeUploadMethodCallListener() has not been implemented.');
  }
}
