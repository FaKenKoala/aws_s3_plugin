import 'package:json_annotation/json_annotation.dart';

part 'upload_success_data.g.dart';

@JsonSerializable()
class UploadSuccessData {
  UploadSuccessData({
    required this.uuid,
    required this.result,
  });
  final String uuid;
  final dynamic result;

  factory UploadSuccessData.fromJson(Map<String, dynamic> json) =>
      _$UploadSuccessDataFromJson(json);
  Map<String, dynamic> toJson() => _$UploadSuccessDataToJson(this);
}
