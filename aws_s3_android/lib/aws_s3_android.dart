// import 'dart:async';

// import 'package:aws_s3_platform_interface/aws_s3_platform_interface.dart';
// import 'package:flutter/foundation.dart';
// import 'package:flutter/material.dart';
// import 'package:flutter/services.dart';

// const MethodChannel _channel = MethodChannel('com.wombat/aws_s3_android');

// class AwsS3Android extends AwsS3Platform {
//   @visibleForTesting
//   MethodChannel get channel => _channel;

//   late BasicMessageChannel messageChannel;
//   bool _isInit = false;

//   EventChannel eventChannel = EventChannel('event_channel');
//   List<StreamSubscription> subscriptions = [];
//   Stream<dynamic>? stream;
//   _init() {
//     _isInit = true;
//     messageChannel =
//         BasicMessageChannel('message_channel', StandardMessageCodec());
//     messageChannel.setMessageHandler((message) async {
//       print('收到host platform的消息: ' + message);
//       throw Exception("再见吧Exception");
//       return "再见吧";
//     });

//     _channel.setMethodCallHandler((call) async {
//       print('收到host发送的调用: ${call.method}, 参数: ${call.arguments}');
//       throw MissingPluginException("没有实现这个方法");
//       throw PlatformException(code: '10', message: '20', details: '30');
//     });
//   }

//   listen() {
//     stream ??= eventChannel.receiveBroadcastStream();

//     int id = subscriptions.length;
//     final subscription = stream!.listen((event) {
//       print('$id 监听到EventChannel新数据: $event');
//     });

//     subscriptions.add(subscription);
//   }

//   static void registerWith() {
//     AwsS3Platform.instance = AwsS3Android();
//   }

//   @override
//   Future<String> calculateMd5(String filePath) async {
//     print('调用android实现的');
//     // if (_isInit == false) {
//     //   _init();
//     // }
//     listen();
//     // final result = await messageChannel.send("发送给host platform");
//     // print('收到host platform的回馈: ' + result);
//     return (await _channel
//         .invokeMethod('calculate_md5', {'filePath': filePath})) as String;
//   }

//   @override
//   initialize(String endpoint, String bucket, Credentials credentials) {
//     return _channel.invokeMethod(
//         'initialize',
//         credentials.toJson()
//           ..addAll({
//             "endpoint": endpoint,
//             "bucket": bucket,
//           }));
//   }

//   @override
//   upload(String fileName, String filePath, String objectKey, String uuid) {
//     return _channel.invokeMethod('upload', {
//       "fileName": fileName,
//       "filePath": filePath,
//       "objectKey": objectKey,
//       "uuid": uuid,
//     });
//   }

//   @override
//   Future<String?> cancelUpload(String uuid, {bool delete = false}) {
//     return _channel.invokeMethod('cancel_upload', {
//       'uuid': uuid,
//       'delete': delete,
//     });
//   }
// }
