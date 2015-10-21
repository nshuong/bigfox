//
//  CSChat.h
//  BigFoxClient
//
//  Created by QuyenNX on 9/29/15.
//  Copyright © 2015 QuyenNX. All rights reserved.
//

#import "MessageOut.h"

//@Message(tag = CS_CHAT, name = @"CS_CHAT")
@interface CSChat : MessageOut
//@Property(name=@"msg")
@property (nonatomic, retain) NSString* msg;

- (id) initWithMessage : (NSString*) msg;
@end
