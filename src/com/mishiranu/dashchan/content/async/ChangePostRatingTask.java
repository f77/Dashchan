package com.mishiranu.dashchan.content.async;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpHolder;

public class ChangePostRatingTask extends HttpHolderTask<Void, Long, Boolean> {

    private Callback callback;

    private String chanName;

    private String boardName;

    private Post post;

    private boolean is_up;

    private ChanPerformer.SendChangePostRatingResult result;

    private String errorMessage;


    /**
     * Run after changing the post's rating.
     */
    public interface Callback {
        public void onChangePostRatingSuccess(ChanPerformer.SendChangePostRatingResult result);

        public void onChangePostRatingFail(String errorMessage);
    }

    public ChangePostRatingTask(Callback callback, String chanName, String boardName, Post post, boolean is_up) {
        this.callback = callback;
        this.chanName = chanName;
        this.boardName = boardName;
        this.post = post;
        this.is_up = is_up;
    }

    @Override
    protected Boolean doInBackground(HttpHolder holder, Void... params) {

        try {
            ChanPerformer.Safe performer = ChanPerformer.get(chanName).safe();
            ChanPerformer.SendChangePostRatingData changePostRatingData = new ChanPerformer.SendChangePostRatingData(holder, this.boardName, this.post, this.is_up);

            result = performer.onChangePostRating(changePostRatingData);
            return true;
        } catch (ExtensionException | HttpException | ApiException | InvalidResponseException e) {
            String msg = e.getMessage();
            this.errorMessage = (msg == null ? e.toString() : msg);
            return false;
        }
    }

    @Override
    public void onPostExecute(Boolean success) {
        if (this.errorMessage != null) {
            this.callback.onChangePostRatingFail(this.errorMessage);
            return;
        }

        this.callback.onChangePostRatingSuccess(this.result);
    }
}
