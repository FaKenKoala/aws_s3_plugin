// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'plain_text_credentials.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PlainTextCredentials _$PlainTextCredentialsFromJson(
        Map<String, dynamic> json) =>
    PlainTextCredentials(
      accessKeyId: json['accessKeyId'] as String,
      secretKeyId: json['secretKeyId'] as String,
      securityToken: json['securityToken'] as String?,
    );

Map<String, dynamic> _$PlainTextCredentialsToJson(
        PlainTextCredentials instance) =>
    <String, dynamic>{
      'accessKeyId': instance.accessKeyId,
      'secretKeyId': instance.secretKeyId,
      'securityToken': instance.securityToken,
    };
