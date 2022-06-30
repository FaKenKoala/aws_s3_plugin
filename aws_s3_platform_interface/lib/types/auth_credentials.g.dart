// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'auth_credentials.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AuthCredentials _$AuthCredentialsFromJson(Map<String, dynamic> json) =>
    AuthCredentials(
      authUrl: json['authUrl'] as String,
      authorization: json['authorization'] as String,
    );

Map<String, dynamic> _$AuthCredentialsToJson(AuthCredentials instance) =>
    <String, dynamic>{
      'authUrl': instance.authUrl,
      'authorization': instance.authorization,
    };
