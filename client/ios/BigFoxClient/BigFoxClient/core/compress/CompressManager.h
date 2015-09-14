//
//  CompressManager.h
//  BigFoxClient
//
//  Created by QuyenNX on 9/4/15.
//  Copyright (c) 2015 QuyenNX. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "ICompress.h"
@interface CompressManager : NSObject
+(CompressManager*) getInstance;
- (char*) compress : (char*) data : (int) length;
- (char*) decompress : (char*) data : (int) length;

@end