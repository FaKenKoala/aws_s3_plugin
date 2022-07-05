import 'package:json_annotation/json_annotation.dart';

part 'upload_init_data.g.dart';

@JsonSerializable()
class UploadInitData {
  UploadInitData({
    required this.uuid,
  });
  final String uuid;
  factory UploadInitData.fromJson(Map<String, dynamic> json) =>
      _$UploadInitDataFromJson(json);
  Map<String, dynamic> toJson() => _$UploadInitDataToJson(this);
}
