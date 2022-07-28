import 'package:json_annotation/json_annotation.dart';

part 'upload_fail_data.g.dart';

@JsonSerializable()
class UploadFailData {
  UploadFailData({
    required this.uuid,
    required this.error,
    this.canceled = false,
    this.delete = false,
  });
  final String uuid;
  final dynamic error;
  final bool canceled;
  final bool delete;

  factory UploadFailData.fromJson(Map<String, dynamic> json) =>
      _$UploadFailDataFromJson(json);
  Map<String, dynamic> toJson() => _$UploadFailDataToJson(this);
}
