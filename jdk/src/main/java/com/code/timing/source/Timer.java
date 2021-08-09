/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util;

import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 任务调度器
 *
 * 调度{@link TimerTask}在后台线程中一次性执行。
 */
public interface Timer {

    /**
     * 调度指定的 TimerTask ，在指定的延迟后执行
     *
     * @return 与指定任务相关联的句柄
     * @throws IllegalStateException      if this timer has been {@linkplain #stop() stopped} already
     * @throws RejectedExecutionException if the pending timeouts are too many and creating new timeout
     *                                    can cause instability in the system.
     */
    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);

    /**
     * 停止所有的还没被执行的定时任务
     *
     * @return 与被此方法取消的任务相关联的句柄
     */
    Set<Timeout> stop();
}
