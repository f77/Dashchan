/*
 * Copyright 2014-2017 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chan.content.model;

import java.util.Collection;

import chan.annotation.Public;
import chan.util.CommonUtils;

// TODO CHAN
// Remove this class after updating
// apachan
// Added: 24.08.16 03:39
@Public
public final class Threads {
    private Posts[] threads;
    private int boardSpeed;

    public Posts[] getThreads() {
        return threads;
    }

    public Threads setThreads(Posts[] threads) {
        this.threads = threads;
        return this;
    }

    @Public
    public int getBoardSpeed() {
        return boardSpeed;
    }

    @Public
    public Threads setBoardSpeed(int boardSpeed) {
        this.boardSpeed = boardSpeed;
        return this;
    }

    @Public
    public Threads(Posts... threads) {
        setThreads(CommonUtils.removeNullItems(threads, Posts.class));
    }

    @Public
    public Threads(Collection<? extends Posts> threads) {
        this(CommonUtils.toArray(threads, Posts.class));
    }
}
