export 'credentials.dart';
export 'auth_credentials.dart';
export 'plain_text_credentials.dart';

abstract class Credentials {
  Map<String, dynamic> toJson();
}
