import 'package:aws_s3_platform_interface/types/credentials.dart';
import 'package:json_annotation/json_annotation.dart';

part 'plain_text_credentials.g.dart';

@JsonSerializable()
class PlainTextCredentials extends Credentials {
  final String accessKeyId;
  final String secretKeyId;
  final String? securityToken;

  PlainTextCredentials({
    required this.accessKeyId,
    required this.secretKeyId,
    this.securityToken,
  });

  Map<String, dynamic> toJson() => _$PlainTextCredentialsToJson(this);
}
