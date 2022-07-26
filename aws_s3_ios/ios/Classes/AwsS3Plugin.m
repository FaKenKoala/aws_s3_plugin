#import "AwsS3Plugin.h"
#import <AWSS3/AWSS3.h>
#import <MobileCoreServices/MobileCoreServices.h>
#import "AuthCredentialsProvider.h"
#import <FileMD5Hash/FileHash.h>

NSString *const TransferUtilityName = @"com.wombat/aws_s3_plugin";

@interface AwsS3Plugin()
{
  FlutterMethodChannel *_mainChannel;
  AWSS3TransferUtility *transferUtility;
  NSString* bucket;
}

@end

@implementation AwsS3Plugin

- (instancetype)init:(NSObject<FlutterPluginRegistrar> *)registrar;
{
  if (self = [super init]) {
    _mainChannel = [FlutterMethodChannel
                    methodChannelWithName:@"com.wombat/aws_s3_plugin"
                    binaryMessenger:[registrar messenger]];
    [AWSDDLog sharedInstance].logLevel = AWSDDLogLevelVerbose;
    //    [AWSDDLog addLogger:[AWSDDTTYLogger sharedInstance]];
    
    [registrar addMethodCallDelegate:self channel:_mainChannel];
  }
  return self;
}

- (BOOL) checkFileExist: (NSString *) filePath {
  NSFileManager* fileManager = [NSFileManager defaultManager];
  return [fileManager fileExistsAtPath:filePath];
}

- (AWSS3TransferUtilityMultiPartProgressBlock)progressBlock: (NSString*) uuid {
  return ^(AWSS3TransferUtilityMultiPartUploadTask *task, NSProgress *progress) {
    NSLog(@"文件上传进度更新，状态: %ld, uuid: %@, transferID: %@, taskIdentifier: %lu", [task status], uuid, [task transferID], (long)[task taskIdentifier]);
    if (task.status != AWSS3TransferUtilityTransferStatusInProgress){
      NSLog(@"上传有进度变化，但是状态不是IN_PROGRESS, 所以取消告知Flutter端");
      return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
      [self->_mainChannel invokeMethod:@"upload_progress" arguments:@{
        @"uuid":uuid,
        @"bytesSent": @0,
        @"totalBytesSent": @(progress.completedUnitCount),
        @"totalBytesExpectedToSend": @(progress.totalUnitCount)}];
      
    });
  };
}

- (AWSS3TransferUtilityMultiPartUploadCompletionHandlerBlock)completionBlock: (NSString*) uuid {
  return ^(AWSS3TransferUtilityMultiPartUploadTask *task, NSError *error) {
    dispatch_async(dispatch_get_main_queue(), ^{
      if (error) {
        [self->_mainChannel invokeMethod:@"upload_fail" arguments:@{@"uuid":uuid, @"error": [error description], @"canceled":@((BOOL)false)}];
      } else {
        [self->_mainChannel invokeMethod:@"upload_success" arguments:@{@"uuid": uuid, @"result":@"upload success"}];
      }
    });
  };
}

-(void)uploadFileName:(NSString *)fileName filePath:(NSString*)filePath objectKey:(NSString *)objectKey uuid:(NSString *)uuid flutterResult: (FlutterResult)flutterResult
{
  NSLog(@"开始上传文件新任务uuid: %@", uuid);
  
  NSURL* fileURL = [NSURL fileURLWithPath:filePath];
  NSString *encodedFileName = [fileName stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
  
  NSString *mimeType = [self mimeTypeFileURL:fileURL];
  if(!mimeType) {
    mimeType = @"application/octet-stream";
  }
  
  AWSS3TransferUtilityMultiPartUploadExpression *expression = [AWSS3TransferUtilityMultiPartUploadExpression new];
  if(encodedFileName) {
    [expression setValue:encodedFileName forRequestHeader:@"x-amz-meta-file-name"];
  }
  
  expression.progressBlock = [self progressBlock:uuid];
  
  //  Create the completion handler for the transfer
  AWSS3TransferUtilityMultiPartUploadCompletionHandlerBlock completionHandler = [self completionBlock:uuid];
  
  dispatch_queue_t uploadQueue = dispatch_queue_create("upload queue", NULL);
  dispatch_async(uploadQueue, ^{
    AWSTask<AWSS3TransferUtilityMultiPartUploadTask *> *task = [self->transferUtility uploadFileUsingMultiPart:fileURL key:objectKey contentType:nil expression:expression completionHandler:completionHandler];
    
    [task waitUntilFinished];
    
    dispatch_async(dispatch_get_main_queue(), ^{
      if (task.error) {
        flutterResult(nil);
        [self->_mainChannel invokeMethod:@"upload_fail" arguments:@{@"uuid":uuid, @"error": [task.error description], @"canceled":@(false)}];
      } else {
        NSLog(@"新建上传结果: uuid: %@, transferID: %@", uuid, [task.result transferID]);
        flutterResult([task.result transferID]);
      }
    });
    
  });
  
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
  NSString *filePath = call.arguments[@"filePath"];
  dispatch_queue_t md5Queue = dispatch_queue_create("calculate md5", NULL);
  dispatch_async(md5Queue, ^{
    NSString *md5 = nil;
    BOOL isDirectory = NO;
    BOOL isExist = [[NSFileManager defaultManager] fileExistsAtPath:filePath isDirectory:&isDirectory];
    if (isDirectory || !isExist) {
      NSLog(@"计算md5的文件路径不存在: %@", filePath);
    } else {
      md5 = [FileHash md5HashOfFileAtPath:filePath];
    }
    
    dispatch_async(dispatch_get_main_queue(), ^{
      result(md5 == NULL ? @"": md5);
    });
  });
}

- (void)getTempFilePathMethodCall:(FlutterMethodCall*) call result:(FlutterResult)result {
  NSString *inputFilePath = call.arguments[@"filePath"];
  
  NSRange tempRange = [inputFilePath rangeOfString:@"/tmp/"];
  NSString* filePathWithoutTempDir = [inputFilePath substringFromIndex:tempRange.location + tempRange.length];
  NSString* filePath = [NSTemporaryDirectory() stringByAppendingString:filePathWithoutTempDir];
  result(filePath);
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
  
  AWSEndpoint* awsEndpoing = [[AWSEndpoint alloc] initWithURLString:endpoint];
  AWSServiceConfiguration* serviceConfiguration = [[AWSServiceConfiguration alloc] initWithRegion:[region aws_regionTypeValue] endpoint:awsEndpoing credentialsProvider:provider];
  
  AWSS3TransferUtilityConfiguration* transferUtilityConfiguration = [AWSS3TransferUtilityConfiguration alloc];
  transferUtilityConfiguration.bucket = bucket;
  transferUtilityConfiguration.retryLimit = 1;
  transferUtilityConfiguration.timeoutIntervalForResource = 7 * 24 * 60 * 60;
  transferUtilityConfiguration.multiPartConcurrencyLimit = @(5);
  
  // 如果aws初始化过，那就暂停所有已经在上传的任务
  if (transferUtility) {
    NSArray<AWSS3TransferUtilityMultiPartUploadTask *> *allUploads = [[transferUtility getMultiPartUploadTasks] result];
    for(AWSS3TransferUtilityMultiPartUploadTask *task in allUploads) {
      [task suspend];
    }
    id<AWSCredentialsProvider> usedProvider = transferUtility.configuration.credentialsProvider;
    if ([usedProvider isKindOfClass:[AuthCredentialsProvider class]] && [provider isKindOfClass:[AuthCredentialsProvider class]]) {
      [(AuthCredentialsProvider*)usedProvider refreshWithAuthServerUrl:((AuthCredentialsProvider*)provider).authServerUrl auhtorization:((AuthCredentialsProvider*)provider).authorization];
    }
  }else {
    [AWSS3TransferUtility registerS3TransferUtilityWithConfiguration:serviceConfiguration transferUtilityConfiguration:transferUtilityConfiguration forKey:TransferUtilityName completionHandler:nil];
    transferUtility = [AWSS3TransferUtility S3TransferUtilityForKey:TransferUtilityName];
  }
  
  result(endpoint);
}

- (void)uploadMethodCall:(FlutterMethodCall*) call result:(FlutterResult)result {
  NSString *fileName = call.arguments[@"fileName"];
  NSString *filePath = call.arguments[@"filePath"];
  NSString *objectKey = call.arguments[@"objectKey"];
  NSString *uuid = call.arguments[@"uuid"];
  NSString *taskId = call.arguments[@"taskId"];
  
  if(taskId){
    NSArray<AWSS3TransferUtilityMultiPartUploadTask *> *allUploads = [[transferUtility getMultiPartUploadTasks] result];
    NSLog(@"当前任务个数: %lu", (unsigned long)[allUploads count]);
    for(AWSS3TransferUtilityMultiPartUploadTask *task in allUploads) {
      NSLog(@"transferID: %@, taskId: %@, status: %ld", [task transferID], taskId, (long)[task status]);
      if ([taskId isEqualToString:[task transferID]]) {
        NSLog(@"找到了,进行恢复");
        [task setProgressBlock: [self progressBlock:uuid]];
        [task setCompletionHandler: [self completionBlock:uuid]];
        [task resume];
        
        result(taskId);
        return;
      }
    }
  }
  
  [self uploadFileName:fileName filePath:filePath objectKey:objectKey uuid:uuid flutterResult:result];
  
}

- (void)pauseMethodCall:(FlutterMethodCall*) call result:(FlutterResult)result {
  
  NSString *uuid = call.arguments[@"uuid"];
  NSString *taskId = call.arguments[@"taskId"];
  if (!transferUtility || !taskId || [taskId length] == 0) {
    result(nil);
    return;
  }
  NSArray<AWSS3TransferUtilityMultiPartUploadTask *> *allUploadTasks = [[transferUtility getMultiPartUploadTasks] result];
  NSLog(@"暂停操作, 当前任务个数: %lu", (unsigned long)[allUploadTasks count]);
  
  for(AWSS3TransferUtilityMultiPartUploadTask *task in allUploadTasks) {
    NSLog(@"transferID: %@, taskId: %@, status: %ld", [task transferID], taskId, (long)[task status]);
    if ([taskId isEqualToString:[task transferID]]) {
      NSLog(@"找到了任务%@, 进行暂停", taskId);
      [task suspend];
      result(taskId);
      return;
    }
  }
  
  result(NULL);
}

- (void)deleteMethodCall:(FlutterMethodCall*) call result:(FlutterResult)result {
  
  NSString *uuid = call.arguments[@"uuid"];
  NSString *taskId = call.arguments[@"taskId"];
  if (!transferUtility || !taskId || [taskId length] == 0) {
    result(nil);
    return;
  }
  NSArray<AWSS3TransferUtilityMultiPartUploadTask *> *allTasks = [[transferUtility getMultiPartUploadTasks] result];
  NSLog(@"删除操作, 当前任务个数: %lu", (unsigned long)[allTasks count]);
  
  for(AWSS3TransferUtilityMultiPartUploadTask *task in allTasks) {
    NSLog(@"transferID: %@, taskId: %@, status: %ld", [task transferID], taskId, (long)[task status]);
    if ([taskId isEqualToString:[task transferID]]) {
      [task cancel];
      NSLog(@"找到了任务%@, 进行删除", taskId);
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
    [self calculateMd5MethodCall:call result:result];
  } else if([@"getTempFilePath" isEqualToString:call.method]){
    [self getTempFilePathMethodCall:call result:result];
  }else if([@"initialize" isEqualToString:call.method]) {
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
