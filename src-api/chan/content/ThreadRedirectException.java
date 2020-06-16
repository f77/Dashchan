/*
 * Copyright 2016-2017 Fukurou Mishiranu
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

package chan.content;

import chan.annotation.Public;

// TODO CHAN
// Remove this class aafter updating
// apachan archiveliom desustorage exach fourplebs krautchan meguca ronery
// Added: 13.10.16 14:55
@Public
public final class ThreadRedirectException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String boardName;
    private final String threadNumber;
    private final String postNumber;

    @Public
    public ThreadRedirectException(String boardName, String threadNumber, String postNumber) {
        this.boardName = boardName;
        this.threadNumber = threadNumber;
        this.postNumber = postNumber;
    }

    @Public
    public ThreadRedirectException(String threadNumber, String postNumber) {
        this(null, threadNumber, postNumber);
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public RedirectException.Target obtainTarget(String chanName, String boardName) throws ExtensionException {
        return RedirectException.toThread(this.boardName != null ? this.boardName : boardName,
                threadNumber, postNumber).obtainTarget(chanName);
    }
}
