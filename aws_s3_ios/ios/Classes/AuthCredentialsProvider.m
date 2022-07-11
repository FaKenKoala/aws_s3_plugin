//
//  AuthCredentialsProvider.m
//  aws_s3_ios
//
//  Created by wombat on 2022/7/11.
//

#import <Foundation/Foundation.h>
#import "AuthCredentialsProvider.h"

@interface AuthCredentialsProvider()

@property (nonatomic, copy) NSString * authServerUrl;
@property (nonatomic, copy) NSString * authorization;

@property (nonatomic, strong) AWSCredentials *internalCredentials;

@property (readonly) BOOL isValid;

@end

@implementation AuthCredentialsProvider

- (instancetype)initWithAuthServerUrl:(NSString *)authServerUrl auhtorization:(NSString *)authorization {
  if (self = [super init]) {
    self.authServerUrl = authServerUrl;
    self.authorization = authorization;
  }
  return self;
}

- (AWSTask<AWSCredentials *> *)credentials {
  if(self.isValid){
    NSLog(@"credentials还有效: %@", [self.internalCredentials description]);
    return [AWSTask taskWithResult:self.internalCredentials];
  }
  NSURL * url = [NSURL URLWithString:self.authServerUrl];
  NSURLRequest * request = [NSURLRequest requestWithURL:url];
  NSMutableURLRequest *mutableRequest = [request mutableCopy];
  [mutableRequest addValue:self.authorization forHTTPHeaderField:@"Authorization"];
  request = [mutableRequest copy];
  
  AWSTaskCompletionSource * tcs = [AWSTaskCompletionSource taskCompletionSource];
  NSURLSession * session = [NSURLSession sharedSession];
  NSURLSessionTask * sessionTask = [session dataTaskWithRequest:request
                                              completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
    if (error) {
      [tcs setError:error];
      return;
    }
    [tcs setResult:data];
  }];
  [sessionTask resume];
  [tcs.task waitUntilFinished];
  
  if (tcs.task.error) {
    return nil;
  } else {
    NSData* data = tcs.task.result;
    
    NSDictionary *object = [NSJSONSerialization JSONObjectWithData:data
                                                           options:kNilOptions
                                                             error:nil];
    NSLog(@"请求结果: %@", [object description]);
    NSString *statusCode = [object objectForKey:@"statusCode"];
    
    if ([[statusCode uppercaseString] isEqualToString:@"OK"]) {
      NSDictionary *data = [object objectForKey:@"data"];
      AWSCredentials* credentials= [[AWSCredentials alloc] initWithAccessKey:[data objectForKey:@"accessKey"] secretKey:[data objectForKey:@"secretKey"] sessionKey:[data objectForKey:@"sessionToken"] expiration:[NSDate dateWithTimeIntervalSince1970: [[data objectForKey:@"expiration"] longLongValue]/1000]];
      self.internalCredentials = credentials;
      return [AWSTask taskWithResult:credentials];
    }else{
      return nil;
    }
  }
}

- (void)invalidateCachedTemporaryCredentials {
  NSLog(@"设置Credentials无效");
  self.internalCredentials = nil;
}

- (BOOL)isValid {
  return self.internalCredentials && ([self.internalCredentials.expiration compare:[NSDate dateWithTimeIntervalSinceNow:10 * 60]] == NSOrderedDescending);
}


@end
