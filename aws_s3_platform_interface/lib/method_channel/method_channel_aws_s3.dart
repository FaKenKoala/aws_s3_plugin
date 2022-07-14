import 'package:aws_s3_platform_interface/aws_s3_platform_interface.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:uuid/uuid.dart';

const MethodChannel _channel = MethodChannel('com.wombat/aws_s3_plugin');

class MethodChannelAWSS3 extends AwsS3Platform {
  @visibleForTesting
  MethodChannel get channel => _channel;

  MethodChannelAWSS3() {
    _channel.setMethodCallHandler(_methodCallHandler);
  }

  final Map<String, UploadMethodCallListener> _uploadMethodCallListeners = {};

  void _notifyUploadListeners(
      void Function(UploadMethodCallListener listener) callback) {
    _notifyListeners(callback, _uploadMethodCallListeners);
  }

  void _notifyListeners<T>(
      void Function(T listener) callback, Map<String, T> listeners) {
    listeners.forEach((key, value) {
      callback(value);
    });
  }

  _toMap(dynamic data) {
    return (data as Map<dynamic, dynamic>)
        .map((key, value) => MapEntry('$key', value));
  }

  Future<dynamic> _methodCallHandler(MethodCall methodCall) async {
    switch (methodCall.method) {

      /// 上传文件
      case 'upload_init':
        UploadInitData initData =
            UploadInitData.fromJson(_toMap(methodCall.arguments));
        debugPrint("上传初始化: ${initData.toJson()}");
        _notifyUploadListeners((listener) => listener.init?.call(initData));
        break;
      case "upload_progress":
        UploadProgressData progressData =
            UploadProgressData.fromJson(_toMap(methodCall.arguments));
        // debugPrint("上传进度: ${progressData.toJson()}");
        _notifyUploadListeners(
            (listener) => listener.progress?.call(progressData));
        break;
      case "upload_success":
        UploadSuccessData successData =
            UploadSuccessData.fromJson(_toMap(methodCall.arguments));
        debugPrint("上传成功: ${successData.toJson()}");
        _notifyUploadListeners(
            (listener) => listener.success?.call(successData));
        break;
      case "upload_fail":
        UploadFailData failData =
            UploadFailData.fromJson(_toMap(methodCall.arguments));
        debugPrint("上传失败: ${failData.toJson()}");
        _notifyUploadListeners((listener) => listener.fail?.call(failData));
        break;
      case "upload_pause":
        _notifyUploadListeners((listener) =>
            listener.pause?.call(methodCall.arguments["objectKey"]));
        break;
      case "upload_resume":
        _notifyUploadListeners((listener) =>
            listener.resume?.call(methodCall.arguments["objectKey"]));
        break;
    }
  }

  @override
  Future<String> calculateMd5(String filePath) async {
    return (await _channel.invokeMethod('calculateMd5', {'filePath': filePath}))
        as String;
  }

  @override
  initialize(
      String endpoint, String bucket, String region, Credentials credentials) {
    return _channel.invokeMethod(
        'initialize',
        credentials.toJson()
          ..addAll({
            "endpoint": endpoint,
            "bucket": bucket,
            "region": region,
          }));
  }

  @override
  Future upload(String fileName, String filePath, String objectKey, String uuid,
      String taskId) {
    return _channel.invokeMethod('upload', {
      "fileName": fileName,
      "filePath": filePath,
      "objectKey": objectKey,
      "uuid": uuid,
      "taskId": taskId,
    });
  }

  @override
  Future pause(String uuid, String taskId) {
    return _channel.invokeMethod('pause', {'uuid': uuid, 'taskId': taskId});
  }

  @override
  Future delete(String uuid, String taskId) {
    return _channel.invokeMethod('delete', {'uuid': uuid, 'taskId': taskId});
  }

  @override
  String addUploadMethodCallListener(UploadMethodCallListener listener) {
    String uuid = Uuid().v4();
    _uploadMethodCallListeners[uuid] = listener;
    return uuid;
  }

  @override
  void removeUploadMethodCallListener({String? id, bool removeAll = false}) {
    if (removeAll) {
      _uploadMethodCallListeners.clear();
    } else {
      _uploadMethodCallListeners.remove(id);
    }
  }
}
