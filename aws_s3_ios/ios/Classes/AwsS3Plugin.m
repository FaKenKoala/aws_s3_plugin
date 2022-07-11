#import "AwsS3Plugin.h"
#import <AWSS3/AWSS3.h>
#import <MobileCoreServices/MobileCoreServices.h>
#import "AuthCredentialsProvider.h"

NSString *const TransferUtilityName = @"com.wombat/aws_s3_plugin";

@interface AwsS3Plugin()
{
  FlutterMethodChannel *_mainChannel;
  NSString *bucket;
  AWSS3TransferUtility *transferUtility;
}

@end

@implementation AwsS3Plugin

- (instancetype)init:(NSObject<FlutterPluginRegistrar> *)registrar;
{
  if (self = [super init]) {
    _mainChannel = [FlutterMethodChannel
                    methodChannelWithName:@"com.wombat/aws_s3_plugin"
                    binaryMessenger:[registrar messenger]];
    [registrar addMethodCallDelegate:self channel:_mainChannel];
  }
  return self;
}

-(void)uploadFileName:(NSString *)fileName filePath:(NSString*)filePath objectKey:(NSString *)objectKey uuid:(NSString *)uuid
{
  NSLog(@"开始上传文件: %@", uuid);
  NSURL *fileURL = [NSURL fileURLWithPath:filePath];
  NSString *encodedFileName = [fileName stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
  
  NSString *mimeType = [self mimeTypeFileURL:fileURL];
  if(!mimeType) {
    mimeType = @"application/octet-stream";
  }
  
  //Create the completion handler for the transfer
  AWSS3TransferUtilityMultiPartUploadCompletionHandlerBlock completionHandler = ^(AWSS3TransferUtilityMultiPartUploadTask *task, NSError *error) {
    dispatch_async(dispatch_get_main_queue(), ^{
      if (error) {
        [self->_mainChannel invokeMethod:@"upload_fail" arguments:@{@"uuid":uuid, @"error": [error description], @"canceled":@(false)}];
      } else {
        [self->_mainChannel invokeMethod:@"upload_success" arguments:@{@"uuid": uuid, @"result":@"upload success"}];
      }
    });
  };
  
  //Create the TransferUtility expression and add the progress block to it.
  //This would be needed to report on progress tracking
  AWSS3TransferUtilityMultiPartUploadExpression *expression = [AWSS3TransferUtilityMultiPartUploadExpression new];
  if(encodedFileName) {
    [expression setValue:encodedFileName forRequestHeader:@"file-name"];
  }
  
  expression.progressBlock = ^(AWSS3TransferUtilityTask *task, NSProgress *progress) {
    dispatch_async(dispatch_get_main_queue(), ^{
      NSLog(@"上传进度: %@", progress);
      [self->_mainChannel invokeMethod:@"upload_progress" arguments:@{
        @"uuid":uuid,
        @"bytesSent": @0,
        @"totalBytesSent": @(progress.completedUnitCount),
        @"totalBytesExpectedToSend": @(progress.totalUnitCount)}];
    });
  };
  
  [transferUtility uploadFileUsingMultiPart:fileURL key:objectKey contentType:mimeType expression:expression completionHandler:completionHandler];
}


- (NSString *)mimeTypeFileURL:(NSURL*) fileURL
{
  NSString *fileExtension = [fileURL pathExtension];
  NSString *UTI = (__bridge_transfer NSString *)UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, (__bridge CFStringRef)fileExtension, NULL);
  NSString *contentType = (__bridge_transfer NSString *)UTTypeCopyPreferredTagWithClass((__bridge CFStringRef)UTI, kUTTagClassMIMEType);
  return contentType;
}

# pragma mark - AwsS3Plugin

- (void)calculateMd5MethodCall:(FlutterMethodCall*) call result:(FlutterResult)result {
  //  NSString *filePath = call.arguments[@"filePath"];
  //  dispatch_queue_t md5Queue = dispatch_queue_create("calculate md5", NULL);
  //  dispatch_async(md5Queue, ^{
  //    NSString *md5 = [ fileMD5String:filePath];
  //    dispatch_async(dispatch_get_main_queue(), ^{
  //      result(md5 == NULL ? @"": md5);
  //    });
  //  });
  
}

- (void)initializeMethodCall:(FlutterMethodCall*) call result:(FlutterResult)result {
  NSString *endpoint = call.arguments[@"endpoint"];
  NSString *region = call.arguments[@"region"];
  if(!region) {
    region = @"us-east-1";
  }
  bucket = call.arguments[@"bucket"];
  NSString *accessKeyId = call.arguments[@"accessKeyId"];
  NSString *secretKeyId = call.arguments[@"secretKeyId"];
  
  id<AWSCredentialsProvider> provider;
  if (accessKeyId != NULL) {
    NSString *securityToken = call.arguments[@"securityToken"];
    if (securityToken != NULL) {
      provider = [[AWSBasicSessionCredentialsProvider alloc] initWithAccessKey:accessKeyId secretKey:secretKeyId sessionToken:securityToken];
    } else {
      provider = [[AWSStaticCredentialsProvider alloc] initWithAccessKey:accessKeyId secretKey:secretKeyId];
    }
  } else {
    NSString *authUrl = call.arguments[@"authUrl"];
    NSString *authorization = call.arguments[@"authorization"];
    provider = [[AuthCredentialsProvider alloc] initWithAuthServerUrl:authUrl auhtorization:authorization];
  }
  
  // 如果ossClient初始化过，那就暂停所有已经在上传的任务
  if (transferUtility) {
    NSArray<AWSS3TransferUtilityUploadTask *> *allUploads = [[transferUtility getUploadTasks] result];
    for(AWSS3TransferUtilityUploadTask *task in allUploads) {
      [task suspend];
    }
    [AWSS3TransferUtility removeS3TransferUtilityForKey:TransferUtilityName];
  }
  
  AWSEndpoint* awsEndpoing = [[AWSEndpoint alloc] initWithURLString:endpoint];
  AWSServiceConfiguration* serviceConfiguration = [[AWSServiceConfiguration alloc] initWithRegion:[region aws_regionTypeValue] endpoint:awsEndpoing credentialsProvider:provider];
  
  AWSS3TransferUtilityConfiguration* transferUtilityConfiguration = [AWSS3TransferUtilityConfiguration alloc];
  transferUtilityConfiguration.bucket = bucket;
  transferUtilityConfiguration.retryLimit = 2;
  transferUtilityConfiguration.multiPartConcurrencyLimit = @(1);
  
  [AWSS3TransferUtility registerS3TransferUtilityWithConfiguration:serviceConfiguration transferUtilityConfiguration:transferUtilityConfiguration forKey:TransferUtilityName completionHandler:nil];
  transferUtility = [AWSS3TransferUtility S3TransferUtilityForKey:TransferUtilityName];
  result(endpoint);
}

- (void)uploadMethodCall:(FlutterMethodCall*) call result:(FlutterResult)result {
  NSString *fileName = call.arguments[@"fileName"];
  NSString *filePath = call.arguments[@"filePath"];
  NSString *objectKey = call.arguments[@"objectKey"];
  NSString *uuid = call.arguments[@"uuid"];
  NSNumber *taskId = call.arguments[@"taskId"];
  
  NSArray<AWSS3TransferUtilityUploadTask *> *allUploads = [[transferUtility getUploadTasks] result];
  
  for( AWSS3TransferUtilityUploadTask *task in allUploads) {
    if ([task taskIdentifier] == [taskId integerValue]) {
      [task resume];
      result(taskId);
      return;
    }
  }
  
  [self uploadFileName:fileName filePath:filePath objectKey:objectKey uuid:uuid];
  
}

- (void)pauseMethodCall:(FlutterMethodCall*) call result:(FlutterResult)result {
  
  NSNumber *taskId = call.arguments[@"taskId"];
  NSArray<AWSS3TransferUtilityUploadTask *> *allUploads = [[transferUtility getUploadTasks] result];
  
  for( AWSS3TransferUtilityUploadTask *task in allUploads) {
    if ([task taskIdentifier] == [taskId integerValue]) {
      [task suspend];
      result(taskId);
      return;
    }
  }
  
  result(NULL);
}

- (void)deleteMethodCall:(FlutterMethodCall*) call result:(FlutterResult)result {
  
  NSNumber *taskId = call.arguments[@"taskId"];
  NSArray<AWSS3TransferUtilityUploadTask *> *allUploads = [[transferUtility getUploadTasks] result];
  
  for( AWSS3TransferUtilityUploadTask *task in allUploads) {
    if ([task taskIdentifier] == [taskId integerValue]) {
      [task cancel];
      result(taskId);
      return;
    }
  }
  
  result(NULL);
}

# pragma mark - FlutterPlugin

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [registrar addApplicationDelegate:[[AwsS3Plugin alloc] init:registrar]];
}

- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
  if ([@"calculateMd5" isEqualToString:call.method]) {
    //    [self calculateMd5MethodCall:call result:result];
    result(FlutterMethodNotImplemented);
  } else if([@"initialize" isEqualToString:call.method]) {
    [self initializeMethodCall:call result:result];
  } else if([@"upload" isEqualToString:call.method]) {
    [self uploadMethodCall:call result:result];
  } else if([@"pause" isEqualToString:call.method]) {
    [self pauseMethodCall:call result:result];
  } else if([@"delete" isEqualToString:call.method]) {
    [self deleteMethodCall:call result:result];
  }else {
    result(FlutterMethodNotImplemented);
  }
}

@end
