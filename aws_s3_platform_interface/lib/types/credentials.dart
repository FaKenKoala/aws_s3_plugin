export 'credentials.dart';

export 'upload/upload_fail_data.dart';
export 'upload/upload_init_data.dart';
export 'upload/upload_method_call_listener.dart';
export 'upload/upload_progress_data.dart';
export 'upload/upload_success_data.dart';

class Credentials {
  final Map<String, dynamic> _json;
  Credentials._(this._json);

  factory Credentials.auth({
    required String authUrl,
    required String authorization,
  }) =>
      Credentials._(<String, dynamic>{
        'authUrl': authUrl,
        'authorization': authorization,
      });

  factory Credentials.plain(
          {required String accessKeyId,
          required String secretKeyId,
          String? securityToken}) =>
      Credentials._(<String, dynamic>{
        'accessKeyId': accessKeyId,
        'secretKeyId': secretKeyId,
        'securityToken': securityToken,
      });

  Map<String, dynamic> toJson() => _json;
}
