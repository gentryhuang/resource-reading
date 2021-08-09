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

/**
 * 与{@link TimerTask}关联的句柄，由{@link Timer}返回。
 */
public interface Timeout {

    /**
     * 返回创建此句柄的{@link Timer}。
     */
    Timer timer();

    /**
     * 返回与此句柄关联的{@link TimerTask}。
     */
    TimerTask task();

    /**
     * 当且仅当与此句柄关联的{@link TimerTask}已过期时返回 true
     */
    boolean isExpired();

    /**
     * 当且仅当与此句柄关联的{@link TimerTask}已被取消时返回 true
     */
    boolean isCancelled();

    /**
     * 尝试取消与此句柄关联的{@link TimerTask}。如果任务已经被执行或取消，它将返回而没有副作用。
     *
     * @return 如果取消成功，则为 true，否则为 false
     */
    boolean cancel();
}
