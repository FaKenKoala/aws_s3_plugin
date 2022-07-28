// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'upload_fail_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UploadFailData _$UploadFailDataFromJson(Map<String, dynamic> json) =>
    UploadFailData(
      uuid: json['uuid'] as String,
      error: json['error'],
      canceled: json['canceled'] as bool? ?? false,
      delete: json['delete'] as bool? ?? false,
    );

Map<String, dynamic> _$UploadFailDataToJson(UploadFailData instance) =>
    <String, dynamic>{
      'uuid': instance.uuid,
      'error': instance.error,
      'canceled': instance.canceled,
      'delete': instance.delete,
    };
