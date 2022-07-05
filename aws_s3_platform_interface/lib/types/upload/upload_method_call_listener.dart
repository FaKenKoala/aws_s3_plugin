import 'upload_fail_data.dart';
import 'upload_init_data.dart';
import 'upload_progress_data.dart';
import 'upload_success_data.dart';

typedef UploadInitListener = void Function(UploadInitData initData);
typedef UploadProgressListener = void Function(UploadProgressData progressData);
typedef UploadSuccessListener = void Function(UploadSuccessData successData);
typedef UploadFailListener = void Function(UploadFailData failData);
typedef UploadPauseListener = void Function(String objectKey);
typedef UploadResumeListener = void Function(String objectKey);

class UploadMethodCallListener {
  UploadMethodCallListener({
    this.init,
    this.progress,
    this.success,
    this.fail,
    this.pause,
    this.resume,
  });

  final UploadInitListener? init;
  final UploadProgressListener? progress;
  final UploadSuccessListener? success;
  final UploadFailListener? fail;
  final UploadPauseListener? pause;
  final UploadResumeListener? resume;
}
