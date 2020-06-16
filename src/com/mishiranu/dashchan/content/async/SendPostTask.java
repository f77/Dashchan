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

package com.mishiranu.dashchan.content.async;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.RecaptchaReader;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.SimilarTextEstimator;

import java.io.Serializable;
import java.util.ArrayList;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.RedirectException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.MultipartEntity;
import chan.text.CommentEditor;

public class SendPostTask extends HttpHolderTask<Void, Long, Boolean> {
    private final String key;
    private final String chanName;
    private final Callback callback;
    private final ChanPerformer.SendPostData data;

    private final boolean progressMode;

    private ChanPerformer.SendPostResult result;
    private ErrorItem errorItem;
    private Serializable extra;
    private boolean captchaError = false;
    private boolean keepCaptcha = false;

    private final TimedProgressHandler progressHandler = new TimedProgressHandler() {
        @Override
        public void onProgressChange(long progress, long progressMax) {
            if (!progressMode) {
                publishProgress(0L, progress, progressMax);
            }
        }

        private final ArrayList<MultipartEntity.Openable> completeOpenables = new ArrayList<>();
        private MultipartEntity.Openable lastOpenable = null;

        @Override
        public void onProgressChange(MultipartEntity.Openable openable, long progress, long progressMax) {
            if (progressMode) {
                if (lastOpenable != openable) {
                    if (lastOpenable != null) {
                        completeOpenables.add(lastOpenable);
                        if (completeOpenables.size() == data.attachments.length) {
                            completeOpenables.clear();
                        }
                    }
                    lastOpenable = openable;
                }
                publishProgress((long) completeOpenables.size(), progress, progressMax);
            }
        }
    };

    public interface Callback {
        public void onSendPostChangeProgressState(String key, ProgressState progressState,
                                                  int attachmentIndex, int attachmentsCount);

        public void onSendPostChangeProgressValue(String key, int progress, int progressMax);

        public void onSendPostSuccess(String key, ChanPerformer.SendPostData data,
                                      String chanName, String threadNumber, String postNumber);

        public void onSendPostFail(String key, ChanPerformer.SendPostData data, String chanName, ErrorItem errorItem,
                                   Serializable extra, boolean captchaError, boolean keepCaptcha);
    }

    public SendPostTask(String key, String chanName, Callback callback, ChanPerformer.SendPostData data) {
        this.key = key;
        this.chanName = chanName;
        this.callback = callback;
        this.data = data;
        progressMode = data.attachments != null;
        if (progressMode) {
            for (ChanPerformer.SendPostData.Attachment attachment : data.attachments) {
                attachment.listener = progressHandler;
            }
        }
    }

    public boolean isProgressMode() {
        return progressMode;
    }

    public enum ProgressState {CONNECTING, SENDING, PROCESSING}

    private ProgressState lastProgressState = ProgressState.CONNECTING;

    private void switchProgressState(ProgressState progressState, int attachmentIndex, boolean force) {
        if (lastProgressState != progressState || force) {
            lastProgressState = progressState;
            if (callback != null) {
                callback.onSendPostChangeProgressState(key, progressState, attachmentIndex, progressMode
                        ? data.attachments.length : 0);
            }
        }
    }

    private void updateProgressValue(int index, long progress, long progressMax) {
        if (progress == 0) {
            switchProgressState(ProgressState.SENDING, index, true);
        } else if (progress == progressMax) {
            switchProgressState(ProgressState.PROCESSING, 0, false);
        }
        if (progressMode && callback != null) {
            callback.onSendPostChangeProgressValue(key, (int) (progress / 1024), (int) (progressMax / 1024));
        }
    }

    @Override
    protected Boolean doInBackground(HttpHolder holder, Void... params) {
        try {
            ChanPerformer.SendPostData data = this.data;
            if (data.captchaData != null && ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(data.captchaType)) {
                String apiKey = data.captchaData.get(ChanPerformer.CaptchaData.API_KEY);
                String recaptchaResponse = data.captchaData.get(ReadCaptchaTask.RECAPTCHA_SKIP_RESPONSE);
                if (apiKey != null && recaptchaResponse == null) {
                    String challenge = data.captchaData.get(ChanPerformer.CaptchaData.CHALLENGE);
                    String input = data.captchaData.get(ChanPerformer.CaptchaData.INPUT);
                    recaptchaResponse = RecaptchaReader.getInstance().getResponseField2(holder, apiKey,
                            challenge, input, Preferences.isRecaptchaJavascript());
                    if (recaptchaResponse == null) {
                        throw new ApiException(ApiException.SEND_ERROR_CAPTCHA);
                    }
                }
                data.captchaData.put(ChanPerformer.CaptchaData.INPUT, recaptchaResponse);
            }
            if (isCancelled()) {
                return false;
            }
            data.holder = holder;
            data.listener = progressHandler;
            ChanPerformer performer = ChanPerformer.get(chanName);
            ChanPerformer.SendPostResult result = performer.safe().onSendPost(data);
            if (data.threadNumber == null && (result == null || result.threadNumber == null)) {
                // New thread created with undefined number
                ChanPerformer.ReadThreadsResult readThreadsResult;
                try {
                    readThreadsResult = performer.safe().onReadThreads(new ChanPerformer
                            .ReadThreadsData(data.boardName, 0, data.holder, null));
                } catch (RedirectException e) {
                    readThreadsResult = null;
                }
                Posts[] threads = readThreadsResult != null ? readThreadsResult.threads : null;
                if (threads != null && threads.length > 0) {
                    String postComment = data.comment;
                    CommentEditor commentEditor = ChanMarkup.get(chanName).safe().obtainCommentEditor(data.boardName);
                    if (commentEditor != null && postComment != null) {
                        postComment = commentEditor.removeTags(postComment);
                    }
                    SimilarTextEstimator estimator = new SimilarTextEstimator(Integer.MAX_VALUE, true);
                    SimilarTextEstimator.WordsData wordsData1 = estimator.getWords(postComment);
                    for (Posts thread : threads) {
                        Post[] posts = thread.getPosts();
                        if (posts != null && posts.length > 0) {
                            Post post = posts[0];
                            String comment = HtmlParser.clear(post.getComment());
                            SimilarTextEstimator.WordsData wordsData2 = estimator.getWords(comment);
                            if (estimator.checkSimiliar(wordsData1, wordsData2)
                                    || wordsData1 == null && wordsData2 == null) {
                                result = new ChanPerformer.SendPostResult(thread.getThreadNumber(), null);
                                break;
                            }
                        }
                    }
                }
            }
            this.result = result;
            return true;
        } catch (ExtensionException | HttpException | InvalidResponseException e) {
            errorItem = e.getErrorItemAndHandle();
            return false;
        } catch (ApiException e) {
            errorItem = e.getErrorItem();
            extra = e.getExtra();
            int errorType = e.getErrorType();
            captchaError = errorType == ApiException.SEND_ERROR_CAPTCHA;
            keepCaptcha = !captchaError && e.checkFlag(ApiException.FLAG_KEEP_CAPTCHA);
            return false;
        } finally {
            ChanConfiguration.get(chanName).commit();
        }
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        int index = values[0].intValue();
        long progress = values[1];
        long progressMax = values[2];
        updateProgressValue(index, progress, progressMax);
    }

    @Override
    protected void onPostExecute(final Boolean result) {
        if (callback != null) {
            if (result) {
                callback.onSendPostSuccess(key, data, chanName, this.result != null ? this.result.threadNumber : null,
                        this.result != null ? this.result.postNumber : null);
            } else {
                callback.onSendPostFail(key, data, chanName, errorItem, extra, captchaError, keepCaptcha);
            }
        }
    }
}
