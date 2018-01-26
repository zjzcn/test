/*
 *  Copyright 2009-2016 Weibo, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.zjzcn.test.transport;

import java.util.concurrent.atomic.AtomicLong;


/**
 * 通过requestId能够知道大致请求的时间
 * <pre>
 * 		目前是 currentTimeMillis * (2^20) + offset.incrementAndGet()
 * 		通过 requestId / (2^20 * 1000) 能够得到秒
 * </pre>
 */
public class RequestId {
	
    private static final AtomicLong offset = new AtomicLong(0);
    private static final int BITS = 20;
    private static final long MAX_COUNT_PER_MILLIS = 1 << BITS;

    public static long newId() {
        long currentTime = System.currentTimeMillis();
        long count = offset.incrementAndGet();
        while(count >= MAX_COUNT_PER_MILLIS){
            synchronized (RequestId.class){
                if(offset.get() >= MAX_COUNT_PER_MILLIS){
                    offset.set(0);
                }
            }
            count = offset.incrementAndGet();
        }
        return (currentTime << BITS) + count;
    }

}