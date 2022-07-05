import 'package:json_annotation/json_annotation.dart';

part 'upload_progress_data.g.dart';

@JsonSerializable()
class UploadProgressData {
  UploadProgressData({
    required this.uuid,
    required this.bytesSent,
    required this.totalBytesSent,
    required this.totalBytesExpectedToSend,
  });
  final String uuid;
  @JsonKey(defaultValue: -1)
  final int bytesSent;
  final int totalBytesSent;
  final int totalBytesExpectedToSend;

  factory UploadProgressData.fromJson(Map<String, dynamic> json) =>
      _$UploadProgressDataFromJson(json);
  Map<String, dynamic> toJson() => _$UploadProgressDataToJson(this);
}
