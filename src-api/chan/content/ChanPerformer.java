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

package chan.content;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Pair;

import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.ui.ForegroundManager;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.content.model.ThreadSummary;
import chan.content.model.Threads;
import chan.http.ChanFileOpenable;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.HttpValidator;
import chan.http.MultipartEntity;
import chan.util.CommonUtils;
import chan.util.StringUtils;

@Extendable
public class ChanPerformer implements ChanManager.Linked {
    private String chanName;

    public static final ChanManager.Initializer INITIALIZER = new ChanManager.Initializer();

    @Public
    public ChanPerformer() {
        this(true);
    }

    ChanPerformer(boolean useInitializer) {
        chanName = useInitializer ? INITIALIZER.consume().chanName : null;
    }

    @Override
    public final String getChanName() {
        return chanName;
    }

    @Override
    public final void init() {
    }

    public static <T extends ChanPerformer> T get(String chanName) {
        return ChanManager.getInstance().getPerformer(chanName, true);
    }

    @Public
    public static <T extends ChanPerformer> T get(Object object) {
        ChanManager manager = ChanManager.getInstance();
        return manager.getPerformer(manager.getLinkedChanName(object), false);
    }

    @Extendable
    protected ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException,
            RedirectException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException,
            RedirectException, ThreadRedirectException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws HttpException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws HttpException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected ReadThreadSummariesResult onReadThreadSummaries(ReadThreadSummariesData data) throws HttpException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException {
        return new ReadContentResult(new HttpRequest(data.uri, data.holder, data).read());
    }

    @Extendable
    protected CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data) throws HttpException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
        return new ReadCaptchaResult(CaptchaState.SKIP, null);
    }

    @Extendable
    protected SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected SendAddToArchiveResult onSendAddToArchive(SendAddToArchiveData data) throws HttpException, ApiException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Extendable
    protected SendChangePostRatingResult onChangePostRating(SendChangePostRatingData data) throws HttpException, ApiException,
            InvalidResponseException {
        throw new UnsupportedOperationException();
    }

    @Public
    public static class ReadThreadsData implements HttpRequest.HolderPreset {
        @Public
        public static final int PAGE_NUMBER_CATALOG = -1;

        @Public
        public final String boardName;
        @Public
        public final int pageNumber;
        @Public
        public final HttpHolder holder;
        @Public
        public final HttpValidator validator;

        public ReadThreadsData(String boardName, int pageNumber, HttpHolder holder, HttpValidator validator) {
            this.boardName = boardName;
            this.pageNumber = pageNumber;
            this.holder = holder;
            this.validator = validator;
        }

        @Public
        public boolean isCatalog() {
            return pageNumber == PAGE_NUMBER_CATALOG;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class ReadThreadsResult {
        public final Posts[] threads;
        public int boardSpeed;
        public HttpValidator validator;

        @Public
        public ReadThreadsResult(Posts... threads) {
            this.threads = CommonUtils.removeNullItems(threads, Posts.class);
        }

        @Public
        public ReadThreadsResult(Collection<Posts> threads) {
            this(CommonUtils.toArray(threads, Posts.class));
        }

        @Public
        public ReadThreadsResult setBoardSpeed(int boardSpeed) {
            this.boardSpeed = boardSpeed;
            return this;
        }

        @Public
        public ReadThreadsResult setValidator(HttpValidator validator) {
            this.validator = validator;
            return this;
        }

        // TODO CHAN
        // Remove this constructor after updating
        // apachan
        // Added: 24.08.16 03:39
        @Public
        public ReadThreadsResult(Threads threads) {
            this(threads != null ? threads.getThreads() : null);
            if (threads != null) {
                boardSpeed = threads.getBoardSpeed();
            }
        }
    }

    @Public
    public static class ReadPostsData implements HttpRequest.HolderPreset {
        @Public
        public final String boardName;
        @Public
        public final String threadNumber;
        @Public
        public final String lastPostNumber;
        @Public
        public final boolean partialThreadLoading;
        @Public
        public final Posts cachedPosts;
        @Public
        public final HttpHolder holder;
        @Public
        public final HttpValidator validator;

        public ReadPostsData(String boardName, String threadNumber, String lastPostNumber, boolean partialThreadLoading,
                             Posts cachedPosts, HttpHolder holder, HttpValidator validator) {
            this.boardName = boardName;
            this.threadNumber = threadNumber;
            this.lastPostNumber = lastPostNumber;
            this.partialThreadLoading = partialThreadLoading;
            this.cachedPosts = cachedPosts;
            this.holder = holder;
            this.validator = validator;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class ReadPostsResult {
        public final Posts posts;
        public HttpValidator validator;
        public boolean fullThread;

        @Public
        public ReadPostsResult(Posts posts) {
            this.posts = posts;
        }

        @Public
        public ReadPostsResult(Post... posts) {
            this((posts = CommonUtils.removeNullItems(posts, Post.class)) != null ? new Posts(posts) : null);
        }

        @Public
        public ReadPostsResult(Collection<Post> posts) {
            this(CommonUtils.toArray(posts, Post.class));
        }

        @Public
        public ReadPostsResult setValidator(HttpValidator validator) {
            this.validator = validator;
            return this;
        }

        @Public
        public ReadPostsResult setFullThread(boolean fullThread) {
            this.fullThread = fullThread;
            return this;
        }
    }

    @Public
    public static class ReadSinglePostData implements HttpRequest.HolderPreset {
        @Public
        public final String boardName;
        @Public
        public final String postNumber;
        @Public
        public final HttpHolder holder;

        public ReadSinglePostData(String boardName, String postNumber, HttpHolder holder) {
            this.boardName = boardName;
            this.postNumber = postNumber;
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class ReadSinglePostResult {
        public final Post post;

        @Public
        public ReadSinglePostResult(Post post) {
            this.post = post;
        }
    }

    @Public
    public static class ReadSearchPostsData implements HttpRequest.HolderPreset {
        @Public
        public final String boardName;
        @Public
        public final String searchQuery;
        @Public
        public final int pageNumber;
        @Public
        public final HttpHolder holder;

        public ReadSearchPostsData(String boardName, String searchQuery, int pageNumber, HttpHolder holder) {
            this.boardName = boardName;
            this.searchQuery = searchQuery;
            this.pageNumber = pageNumber;
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class ReadSearchPostsResult {
        public final Post[] posts;

        @Public
        public ReadSearchPostsResult(Post... posts) {
            this.posts = CommonUtils.removeNullItems(posts, Post.class);
        }

        @Public
        public ReadSearchPostsResult(Collection<Post> posts) {
            this(CommonUtils.toArray(posts, Post.class));
        }
    }

    @Public
    public static final class ReadBoardsData implements HttpRequest.HolderPreset {
        @Public
        public final HttpHolder holder;

        public ReadBoardsData(HttpHolder holder) {
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class ReadBoardsResult {
        public final BoardCategory[] boardCategories;

        @Public
        public ReadBoardsResult(BoardCategory... boardCategories) {
            if (boardCategories != null) {
                for (int i = 0; i < boardCategories.length; i++) {
                    if (boardCategories[i] != null) {
                        Board[] boards = boardCategories[i].getBoards();
                        if (boards == null || boards.length == 0) {
                            boardCategories[i] = null;
                        }
                    }
                }
            }
            this.boardCategories = CommonUtils.removeNullItems(boardCategories, BoardCategory.class);
        }

        @Public
        public ReadBoardsResult(Collection<BoardCategory> boardCategories) {
            this(CommonUtils.toArray(boardCategories, BoardCategory.class));
        }
    }

    @Public
    public static class ReadUserBoardsData implements HttpRequest.HolderPreset {
        @Public
        public final HttpHolder holder;

        public ReadUserBoardsData(HttpHolder holder) {
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class ReadUserBoardsResult {
        public final Board[] boards;

        @Public
        public ReadUserBoardsResult(Board... boards) {
            this.boards = CommonUtils.removeNullItems(boards, Board.class);
        }

        @Public
        public ReadUserBoardsResult(Collection<Board> boards) {
            this(CommonUtils.toArray(boards, Board.class));
        }
    }

    @Public
    public static class ReadThreadSummariesData implements HttpRequest.HolderPreset {
        @Public
        public static final int TYPE_ARCHIVED_THREADS = 0;

        @Public
        public final String boardName;
        @Public
        public final int pageNumber;
        @Public
        public final int type;
        @Public
        public final HttpHolder holder;

        public ReadThreadSummariesData(String boardName, int pageNumber, int type, HttpHolder holder) {
            this.boardName = boardName;
            this.pageNumber = pageNumber;
            this.type = type;
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class ReadThreadSummariesResult {
        public final ThreadSummary[] threadSummaries;

        @Public
        public ReadThreadSummariesResult(ThreadSummary... threadSummaries) {
            this.threadSummaries = CommonUtils.removeNullItems(threadSummaries, ThreadSummary.class);
        }

        @Public
        public ReadThreadSummariesResult(Collection<ThreadSummary> threadSummaries) {
            this(CommonUtils.toArray(threadSummaries, ThreadSummary.class));
        }
    }

    @Public
    public static class ReadPostsCountData implements HttpRequest.HolderPreset, HttpRequest.TimeoutsPreset {
        @Public
        public final String boardName;
        @Public
        public final String threadNumber;
        public final int connectTimeout;
        public final int readTimeout;
        @Public
        public final HttpHolder holder;
        @Public
        public final HttpValidator validator;

        public ReadPostsCountData(String boardName, String threadNumber, int connectTimeout, int readTimeout,
                                  HttpHolder holder, HttpValidator validator) {
            this.boardName = boardName;
            this.threadNumber = threadNumber;
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
            this.holder = holder;
            this.validator = validator;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }

        @Override
        public int getConnectTimeout() {
            return connectTimeout;
        }

        @Override
        public int getReadTimeout() {
            return readTimeout;
        }
    }

    @Public
    public static final class ReadPostsCountResult {
        public final int postsCount;
        public HttpValidator validator;

        @Public
        public ReadPostsCountResult(int postsCount) {
            this.postsCount = postsCount;
        }

        @Public
        public ReadPostsCountResult setValidator(HttpValidator validator) {
            this.validator = validator;
            return this;
        }
    }

    @Public
    public static class ReadContentData implements HttpRequest.HolderPreset, HttpRequest.TimeoutsPreset,
            HttpRequest.InputListenerPreset, HttpRequest.OutputStreamPreset {
        @Public
        public final Uri uri;
        public final int connectTimeout;
        public final int readTimeout;
        @Public
        public final HttpHolder holder;
        public final HttpHolder.InputListener listener;
        public final OutputStream outputStream;

        public ReadContentData(Uri uri, int connectTimeout, int readTimeout, HttpHolder holder,
                               HttpHolder.InputListener listener, OutputStream outputStream) {
            this.uri = uri;
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
            this.holder = holder;
            this.listener = listener;
            this.outputStream = outputStream;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }

        @Override
        public int getConnectTimeout() {
            return connectTimeout;
        }

        @Override
        public int getReadTimeout() {
            return readTimeout;
        }

        @Override
        public HttpHolder.InputListener getInputListener() {
            return listener;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }
    }

    @Public
    public static final class ReadContentResult {
        public final HttpResponse response;

        @Public
        public ReadContentResult(HttpResponse response) {
            this.response = response;
        }
    }

    @Public
    public static class CheckAuthorizationData implements HttpRequest.HolderPreset {
        @Public
        public static final int TYPE_CAPTCHA_PASS = 0;
        @Public
        public static final int TYPE_USER_AUTHORIZATION = 1;

        @Public
        public final int type;
        @Public
        public final String[] authorizationData;
        @Public
        public final HttpHolder holder;

        public CheckAuthorizationData(int type, String[] authorizationData, HttpHolder holder) {
            this.type = type;
            this.authorizationData = authorizationData;
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class CheckAuthorizationResult {
        public final boolean success;

        @Public
        public CheckAuthorizationResult(boolean success) {
            this.success = success;
        }
    }

    @Public
    public static class ReadCaptchaData implements HttpRequest.HolderPreset {
        @Public
        public final String captchaType;
        @Public
        public final String[] captchaPass;
        @Public
        public final boolean mayShowLoadButton;
        @Public
        public final String requirement;
        @Public
        public final String boardName;
        @Public
        public final String threadNumber;
        @Public
        public final HttpHolder holder;

        public ReadCaptchaData(String captchaType, String[] captchaPass, boolean mayShowLoadButton,
                               String requirement, String boardName, String threadNumber, HttpHolder holder) {
            this.captchaType = captchaType;
            this.captchaPass = captchaPass;
            this.mayShowLoadButton = mayShowLoadButton;
            this.requirement = requirement;
            this.boardName = boardName;
            this.threadNumber = threadNumber;
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public enum CaptchaState {
        @Public CAPTCHA,
        @Public SKIP,
        @Public PASS,
        @Public NEED_LOAD
    }

    @Public
    public static final class ReadCaptchaResult {
        public final CaptchaState captchaState;
        public final CaptchaData captchaData;

        public String captchaType;
        public ChanConfiguration.Captcha.Input input;
        public ChanConfiguration.Captcha.Validity validity;
        public Bitmap image;
        public boolean large;

        @Public
        public ReadCaptchaResult(CaptchaState captchaState, CaptchaData captchaData) {
            this.captchaState = captchaState;
            this.captchaData = captchaData;
        }

        @Public
        public ReadCaptchaResult setCaptchaType(String captchaType) {
            this.captchaType = captchaType;
            return this;
        }

        @Public
        public ReadCaptchaResult setInput(ChanConfiguration.Captcha.Input input) {
            this.input = input;
            return this;
        }

        @Public
        public ReadCaptchaResult setValidity(ChanConfiguration.Captcha.Validity validity) {
            this.validity = validity;
            return this;
        }

        @Public
        public ReadCaptchaResult setImage(Bitmap image) {
            this.image = image;
            return this;
        }

        @Public
        public ReadCaptchaResult setLarge(boolean large) {
            this.large = large;
            return this;
        }
    }

    @Public
    public static class CaptchaData implements Serializable {
        private static final long serialVersionUID = 1L;

        @Public
        public static final String CHALLENGE = "challenge";
        @Public
        public static final String INPUT = "input";
        @Public
        public static final String API_KEY = "api_key";
        @Public
        public static final String REFERER = "referer";

        private final HashMap<String, String> data = new HashMap<>();

        @Public
        public void put(String key, String value) {
            data.put(key, value);
        }

        @Public
        public String get(String key) {
            return data.get(key);
        }
    }

    @Public
    public static class SendPostData implements HttpRequest.HolderPreset, HttpRequest.TimeoutsPreset,
            HttpRequest.OutputListenerPreset {
        @Public
        public final String boardName;
        @Public
        public final String threadNumber;

        @Public
        public final String subject;
        @Public
        public final String comment;
        @Public
        public final String name;
        @Public
        public final String email;
        @Public
        public final String password;

        @Public
        public final Attachment[] attachments;

        @Public
        public final boolean optionSage;
        @Public
        public final boolean optionSpoiler;
        @Public
        public final boolean optionOriginalPoster;
        @Public
        public final String userIcon;

        @Public
        public final String captchaType;
        @Public
        public final CaptchaData captchaData;

        public final int connectTimeout;
        public final int readTimeout;
        @Public
        public HttpHolder holder;
        public HttpRequest.OutputListener listener;

        @Public
        public static class Attachment {
            public final FileHolder fileHolder;
            public final String fileName;
            @Public
            public final String rating;

            public final boolean optionUniqueHash;
            public final boolean optionRemoveMetadata;
            public final boolean optionRemoveFileName;
            @Public
            public final boolean optionSpoiler;
            public final GraphicsUtils.Reencoding reencoding;

            public MultipartEntity.OpenableOutputListener listener;

            private ChanFileOpenable openable;

            public Attachment(FileHolder fileHolder, String fileName, String rating, boolean optionUniqueHash,
                              boolean optionRemoveMetadata, boolean optionRemoveFileName, boolean optionSpoiler,
                              GraphicsUtils.Reencoding reencoding) {
                this.fileHolder = fileHolder;
                this.fileName = fileName;
                this.rating = rating;
                this.optionUniqueHash = optionUniqueHash;
                this.optionRemoveMetadata = optionRemoveMetadata;
                this.optionRemoveFileName = optionRemoveFileName;
                this.optionSpoiler = optionSpoiler;
                this.reencoding = reencoding;
            }

            private void ensureOpenable() {
                if (openable == null) {
                    openable = new ChanFileOpenable(fileHolder, fileName, optionUniqueHash, optionRemoveMetadata,
                            optionRemoveFileName, reencoding);
                }
            }

            @Public
            public void addToEntity(MultipartEntity entity, String name) {
                ensureOpenable();
                entity.add(name, openable, listener);
            }

            @Public
            public String getFileName() {
                ensureOpenable();
                return openable.getFileName();
            }

            @Public
            public String getMimeType() {
                ensureOpenable();
                return openable.getMimeType();
            }

            @Public
            public InputStream openInputSteam() throws IOException {
                ensureOpenable();
                return openable.openInputStream();
            }

            @Public
            public InputStream openInputSteamForSending() throws IOException {
                InputStream inputStream = openInputSteam();
                if (listener != null) {
                    return new InputStreamForSending(inputStream, getSize());
                } else {
                    return inputStream;
                }
            }

            @Public
            public long getSize() {
                ensureOpenable();
                return openable.getSize();
            }

            @Public
            public Pair<Integer, Integer> getImageSize() {
                ensureOpenable();
                int width = openable.getImageWidth();
                int height = openable.getImageHeight();
                if (width > 0 && height > 0) {
                    return new Pair<>(width, height);
                } else {
                    return null;
                }
            }

            private class InputStreamForSending extends InputStream {
                private final InputStream inputStream;
                private final long progressMax;

                public InputStreamForSending(InputStream inputStream, long progressMax) {
                    this.inputStream = inputStream;
                    this.progressMax = progressMax;
                }

                private long progress = 0;

                private void notify(int count) {
                    progress += count;
                    listener.onOutputProgressChange(openable, progress, progressMax);
                }

                @Override
                public int read() throws IOException {
                    int result = inputStream.read();
                    if (result != -1) {
                        notify(1);
                    }
                    return result;
                }

                @Override
                public int read(byte[] buffer) throws IOException {
                    return read(buffer, 0, buffer.length);
                }

                @Override
                public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
                    int result = inputStream.read(buffer, byteOffset, byteCount);
                    if (result > 0) {
                        notify(result);
                    }
                    return result;
                }

                @Override
                public void close() throws IOException {
                    inputStream.close();
                }
            }

            @Override
            public boolean equals(Object o) {
                if (o == this) {
                    return true;
                }
                if (o instanceof Attachment) {
                    Attachment attachment = (Attachment) o;
                    return attachment.fileHolder.equals(fileHolder) && StringUtils.equals(attachment.rating, rating) &&
                            attachment.optionUniqueHash == optionUniqueHash && attachment.optionRemoveMetadata ==
                            optionRemoveMetadata && attachment.optionRemoveFileName == optionRemoveFileName &&
                            attachment.optionSpoiler == optionSpoiler;
                }
                return false;
            }

            @Override
            public int hashCode() {
                int prime = 31;
                int result = 1;
                result = prime * result + fileHolder.hashCode();
                result = prime * result + (rating != null ? rating.hashCode() : 0);
                result = prime * result + (optionUniqueHash ? 1 : 0);
                result = prime * result + (optionRemoveMetadata ? 1 : 0);
                result = prime * result + (optionRemoveFileName ? 1 : 0);
                result = prime * result + (optionSpoiler ? 1 : 0);
                return result;
            }
        }

        public SendPostData(String boardName, String threadNumber, String subject, String comment,
                            String name, String email, String password, Attachment[] attachments, boolean optionSage,
                            boolean optionSpoiler, boolean optionOriginalPoster, String userIcon, String captchaType,
                            CaptchaData captchaData, int connectTimeout, int readTimeout) {
            this.boardName = boardName;
            this.threadNumber = threadNumber;
            this.subject = subject;
            this.comment = comment;
            this.name = name;
            this.email = email;
            this.password = password;
            this.attachments = attachments;
            this.optionSage = optionSage;
            this.optionSpoiler = optionSpoiler;
            this.optionOriginalPoster = optionOriginalPoster;
            this.userIcon = userIcon;
            this.captchaType = captchaType;
            this.captchaData = captchaData;
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }

        @Override
        public int getConnectTimeout() {
            return connectTimeout;
        }

        @Override
        public int getReadTimeout() {
            return readTimeout;
        }

        @Override
        public HttpRequest.OutputListener getOutputListener() {
            return listener;
        }
    }

    @Public
    public static final class SendPostResult {
        public final String threadNumber;
        public final String postNumber;

        @Public
        public SendPostResult(String threadNumber, String postNumber) {
            this.threadNumber = threadNumber;
            this.postNumber = postNumber;
        }
    }

    @Public
    public static class SendDeletePostsData implements HttpRequest.HolderPreset {
        @Public
        public final String boardName;
        @Public
        public final String threadNumber;
        @Public
        public final List<String> postNumbers;
        @Public
        public final String password;
        @Public
        public final boolean optionFilesOnly;
        @Public
        public final HttpHolder holder;

        public SendDeletePostsData(String boardName, String threadNumber, List<String> postNumbers, String password,
                                   boolean optionFilesOnly, HttpHolder holder) {
            this.boardName = boardName;
            this.threadNumber = threadNumber;
            this.postNumbers = postNumbers;
            this.password = password;
            this.optionFilesOnly = optionFilesOnly;
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class SendDeletePostsResult {
        @Public
        public SendDeletePostsResult() {
        }
    }

    @Public
    public static class SendReportPostsData implements HttpRequest.HolderPreset {
        @Public
        public final String boardName;
        @Public
        public final String threadNumber;
        @Public
        public final List<String> postNumbers;
        @Public
        public final String type;
        @Public
        public final List<String> options;
        @Public
        public final String comment;
        @Public
        public final HttpHolder holder;

        public SendReportPostsData(String boardName, String threadNumber, List<String> postNumbers, String type,
                                   List<String> options, String comment, HttpHolder holder) {
            this.boardName = boardName;
            this.threadNumber = threadNumber;
            this.postNumbers = postNumbers;
            this.type = type;
            this.options = options != null ? Collections.unmodifiableList(options) : null;
            this.comment = comment;
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class SendReportPostsResult {
        @Public
        public SendReportPostsResult() {
        }
    }

    @Public
    public static class SendAddToArchiveData implements HttpRequest.HolderPreset {
        @Public
        public final Uri uri;
        @Public
        public final String boardName;
        @Public
        public final String threadNumber;
        @Public
        public final List<String> options;
        @Public
        public final HttpHolder holder;

        public SendAddToArchiveData(Uri uri, String boardName, String threadNumber, List<String> options,
                                    HttpHolder holder) {
            this.uri = uri;
            this.boardName = boardName;
            this.threadNumber = threadNumber;
            this.options = options != null ? Collections.unmodifiableList(options) : null;
            this.holder = holder;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class SendAddToArchiveResult {
        public final String boardName;
        public final String threadNumber;

        @Public
        public SendAddToArchiveResult(String boardName, String threadNumber) {
            this.boardName = boardName;
            this.threadNumber = threadNumber;
        }
    }

    @Public
    public static class SendChangePostRatingData implements HttpRequest.HolderPreset {
        @Public
        public final HttpHolder holder;
        @Public
        public final String boardName;
        @Public
        public final Post post;
        @Public
        public final boolean is_up;

        public SendChangePostRatingData(HttpHolder holder, String boardName, Post post, boolean is_up) {
            this.holder = holder;
            this.boardName = boardName;
            this.post = post;
            this.is_up = is_up;
        }

        @Override
        public HttpHolder getHolder() {
            return holder;
        }
    }

    @Public
    public static final class SendChangePostRatingResult {
        public final boolean isSuccess;
        public final String message;

        @Public
        public SendChangePostRatingResult(boolean isSuccess, String message) {
            this.isSuccess = isSuccess;
            this.message = message;
        }
    }

    private void checkPerformerRequireCall() {
        if (ConcurrentUtils.isMain()) {
            throw new RuntimeException("Invalid call");
        }
    }

    @Public
    public final CaptchaData requireUserCaptcha(String requirement, String boardName, String threadNumber,
                                                boolean retry) {
        checkPerformerRequireCall();
        return ForegroundManager.getInstance().requireUserCaptcha(this, requirement, boardName, threadNumber, retry);
    }

    @Public
    public final Integer requireUserItemSingleChoice(int selected, CharSequence[] item, String descriptionText,
                                                     Bitmap descriptionImage) {
        checkPerformerRequireCall();
        return ForegroundManager.getInstance().requireUserItemSingleChoice(selected, item,
                descriptionText, descriptionImage);
    }

    @Public
    public final boolean[] requireUserItemMultipleChoice(boolean[] selected, CharSequence[] item,
                                                         String descriptionText, Bitmap descriptionImage) {
        checkPerformerRequireCall();
        return ForegroundManager.getInstance().requireUserItemMultipleChoice(selected, item,
                descriptionText, descriptionImage);
    }

    @Public
    public final Integer requireUserImageSingleChoice(int selected, Bitmap[] images, String descriptionText,
                                                      Bitmap descriptionImage) {
        checkPerformerRequireCall();
        return ForegroundManager.getInstance().requireUserImageSingleChoice(3, selected, images,
                descriptionText, descriptionImage);
    }

    @Public
    public final boolean[] requireUserImageMultipleChoice(boolean[] selected, Bitmap[] images,
                                                          String descriptionText, Bitmap descriptionImage) {
        checkPerformerRequireCall();
        return ForegroundManager.getInstance().requireUserImageMultipleChoice(3, selected, images,
                descriptionText, descriptionImage);
    }

    public static final class Safe {
        private final ChanPerformer performer;

        private Safe(ChanPerformer performer) {
            this.performer = performer;
        }

        public ReadThreadsResult onReadThreads(ReadThreadsData data) throws ExtensionException, HttpException,
                InvalidResponseException, RedirectException {
            try {
                return performer.onReadThreads(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public ReadPostsResult onReadPosts(ReadPostsData data) throws ExtensionException, HttpException,
                InvalidResponseException, RedirectException, ThreadRedirectException {
            try {
                return performer.onReadPosts(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public ReadSinglePostResult onReadSinglePost(ReadSinglePostData data) throws ExtensionException, HttpException,
                InvalidResponseException {
            try {
                return performer.onReadSinglePost(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws ExtensionException,
                HttpException, InvalidResponseException {
            try {
                return performer.onReadSearchPosts(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public ReadBoardsResult onReadBoards(ReadBoardsData data) throws ExtensionException, HttpException,
                InvalidResponseException {
            try {
                return performer.onReadBoards(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws ExtensionException, HttpException,
                InvalidResponseException {
            try {
                return performer.onReadUserBoards(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public ReadThreadSummariesResult onReadThreadSummaries(ReadThreadSummariesData data) throws ExtensionException,
                HttpException, InvalidResponseException {
            try {
                return performer.onReadThreadSummaries(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws ExtensionException, HttpException,
                InvalidResponseException {
            try {
                return performer.onReadPostsCount(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public ReadContentResult onReadContent(ReadContentData data) throws ExtensionException, HttpException,
                InvalidResponseException {
            try {
                return performer.onReadContent(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data) throws ExtensionException,
                HttpException, InvalidResponseException {
            try {
                return performer.onCheckAuthorization(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws ExtensionException, HttpException,
                InvalidResponseException {
            try {
                return performer.onReadCaptcha(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public SendPostResult onSendPost(SendPostData data) throws ExtensionException, HttpException, ApiException,
                InvalidResponseException {
            try {
                return performer.onSendPost(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws ExtensionException,
                HttpException, ApiException, InvalidResponseException {
            try {
                return performer.onSendDeletePosts(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws ExtensionException,
                HttpException, ApiException, InvalidResponseException {
            try {
                return performer.onSendReportPosts(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public SendAddToArchiveResult onSendAddToArchive(SendAddToArchiveData data) throws ExtensionException,
                HttpException, ApiException, InvalidResponseException {
            try {
                return performer.onSendAddToArchive(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }

        public SendChangePostRatingResult onChangePostRating(SendChangePostRatingData data) throws ExtensionException,
                HttpException, ApiException, InvalidResponseException {
            try {
                return performer.onChangePostRating(data);
            } catch (LinkageError | RuntimeException e) {
                throw new ExtensionException(e);
            }
        }
    }

    private final Safe safe = new Safe(this);

    public final Safe safe() {
        return safe;
    }
}
