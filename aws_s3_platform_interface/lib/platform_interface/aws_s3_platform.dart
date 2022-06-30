import 'dart:html';

import 'package:aws_s3_platform_interface/method_channel/method_channel_aws_s3.dart';
import 'package:aws_s3_platform_interface/types/credentials.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class AWSS3Platform extends PlatformInterface {
  AWSS3Platform() : super(token: _token);

  static final Object _token = Object();

  static AWSS3Platform _instance = MethodChannelAWSS3();
  AWSS3Platform get instance => _instance;

  set instance(AWSS3Platform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  calculateMd5(String filePath) {
    throw UnimplementedError("calculateMd5() has not been implemented.");
  }

  initialize(String endpoint, String bucket, Credentials credentials) {
    throw UnimplementedError('initialize() has not been implemented.');
  }

  upload(String fileName, String filePath, String objectKey, String uuid) {
    throw UnimplementedError('upload() has not been implemented.');
  }

  Future<String?> cancelUpload(String uuid, {bool delete = false}) {
    throw UnimplementedError('cancelUpload() has not been implemented.');
  }
}
