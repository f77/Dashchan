/*
 * Copyright 2014-2016 Fukurou Mishiranu
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

package com.mishiranu.dashchan.content.async;

import com.mishiranu.dashchan.content.model.ErrorItem;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Board;
import chan.http.HttpException;
import chan.http.HttpHolder;

public class ReadUserBoardsTask extends HttpHolderTask<Void, Long, Boolean> {
    private final String chanName;
    private final Callback callback;

    private Board[] boards;
    private ErrorItem errorItem;

    public interface Callback {
        public void onReadUserBoardsSuccess(Board[] boards);

        public void onReadUserBoardsFail(ErrorItem errorItem);
    }

    public ReadUserBoardsTask(String chanName, Callback callback) {
        this.chanName = chanName;
        this.callback = callback;
    }

    @Override
    protected Boolean doInBackground(HttpHolder holder, Void... params) {
        try {
            ChanPerformer.ReadUserBoardsResult result = ChanPerformer.get(chanName).safe()
                    .onReadUserBoards(new ChanPerformer.ReadUserBoardsData(holder));
            Board[] boards = result != null ? result.boards : null;
            if (boards != null && boards.length == 0) {
                boards = null;
            }
            if (boards != null) {
                ChanConfiguration.get(chanName).updateFromBoards(boards);
            }
            this.boards = boards;
            return true;
        } catch (ExtensionException | HttpException | InvalidResponseException e) {
            errorItem = e.getErrorItemAndHandle();
            return false;
        } finally {
            ChanConfiguration.get(chanName).commit();
        }
    }

    @Override
    public void onPostExecute(Boolean success) {
        if (success) {
            if (boards != null && boards.length > 0) {
                callback.onReadUserBoardsSuccess(boards);
            } else {
                callback.onReadUserBoardsFail(new ErrorItem(ErrorItem.TYPE_EMPTY_RESPONSE));
            }
        } else {
            callback.onReadUserBoardsFail(errorItem);
        }
    }
}
