//
//  AuthCredentialsProvider.h
//  aws_s3_ios
//
//  Created by wombat on 2022/7/11.
//

#ifndef AuthCredentialsProvider_h
#define AuthCredentialsProvider_h

#import <AWSS3/AWSS3.h>

@interface AuthCredentialsProvider : NSObject<AWSCredentialsProvider>

@property (nonatomic, copy) NSString * authServerUrl;
@property (nonatomic, copy) NSString * authorization;

- (instancetype)initWithAuthServerUrl:(NSString *)authServerUrl auhtorization:(NSString *)authorization;
- (void)refreshWithAuthServerUrl:(NSString *)authServerUrl auhtorization:(NSString *)authorization;
@end

#endif /* AuthCredentialsProvider_h */
