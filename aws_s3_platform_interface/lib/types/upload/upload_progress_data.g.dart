// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'upload_progress_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UploadProgressData _$UploadProgressDataFromJson(Map<String, dynamic> json) =>
    UploadProgressData(
      uuid: json['uuid'] as String,
      bytesSent: json['bytesSent'] as int? ?? -1,
      totalBytesSent: json['totalBytesSent'] as int,
      totalBytesExpectedToSend: json['totalBytesExpectedToSend'] as int,
    );

Map<String, dynamic> _$UploadProgressDataToJson(UploadProgressData instance) =>
    <String, dynamic>{
      'uuid': instance.uuid,
      'bytesSent': instance.bytesSent,
      'totalBytesSent': instance.totalBytesSent,
      'totalBytesExpectedToSend': instance.totalBytesExpectedToSend,
    };
