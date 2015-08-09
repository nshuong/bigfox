/*
 * Author: HuongNS
 * Copyright @ 2015 by OneSoft.,JSC
 * 
 */
package vn.com.onesoft.livetube.io.message.sc;

import vn.com.onesoft.livetube.io.message.core.MessageOut;
import vn.com.onesoft.livetube.io.message.core.Tags;
import vn.com.onesoft.livetube.io.message.core.annotations.Message;
import vn.com.onesoft.livetube.io.message.core.annotations.Property;

/**
 *
 * @author HuongNS
 */

@Message(tag = Tags.SC_LIKE, name = "SC_LIKE")
public class SCLike extends MessageOut {

    @Property(name = "responseCode")
    public int responseCode;
    @Property(name = "videoId")
    public int videoId;
    @Property(name = "currentStatus")
    public int currentStatus; // 0: unlike, 1: like
    public static final int STATUS_UNLIKE = 0;
    public static final int STATUS_LIKE = 1;

    public SCLike(int responseCode, int videoId, int currentStatus) {
        super();
        this.responseCode = responseCode;
        this.videoId = videoId;
        this.currentStatus = currentStatus;
    }

}
