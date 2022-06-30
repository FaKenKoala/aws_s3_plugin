import 'package:aws_s3_platform_interface/types/credentials.dart';
import 'package:json_annotation/json_annotation.dart';

part 'auth_credentials.g.dart';

@JsonSerializable()
class AuthCredentials extends Credentials {
  AuthCredentials({
    required this.authUrl,
    required this.authorization,
  });

  final String authUrl;
  final String authorization;

  Map<String, dynamic> toJson() => _$AuthCredentialsToJson(this);
}
