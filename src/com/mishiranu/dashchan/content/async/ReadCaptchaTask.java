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

import android.graphics.Bitmap;
import android.util.Pair;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.net.CaptchaServiceReader;
import com.mishiranu.dashchan.content.net.RecaptchaReader;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.GraphicsUtils;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;

public class ReadCaptchaTask extends HttpHolderTask<Void, Long, Boolean> {
    public static final String RECAPTCHA_SKIP_RESPONSE = "recaptcha_skip_response";

    private final Callback callback;
    private final CaptchaReader captchaReader;

    private final String captchaType;
    private final String[] captchaPass;
    private final boolean mayShowLoadButton;
    private final String requirement;
    private final String chanName;
    private final String boardName;
    private final String threadNumber;

    private ChanPerformer.CaptchaState captchaState;
    private ChanPerformer.CaptchaData captchaData;
    private String loadedCaptchaType;
    private ChanConfiguration.Captcha.Input input;
    private ChanConfiguration.Captcha.Validity validity;
    private Bitmap image;
    private boolean large;
    private boolean blackAndWhite;
    private ErrorItem errorItem;

    public interface Callback {
        public void onReadCaptchaSuccess(ChanPerformer.CaptchaState captchaState, ChanPerformer.CaptchaData captchaData,
                                         String captchaType, ChanConfiguration.Captcha.Input input, ChanConfiguration.Captcha.Validity validity,
                                         Bitmap image, boolean large, boolean blackAndWhite);

        public void onReadCaptchaError(ErrorItem errorItem);
    }

    public interface CaptchaReader {
        public ChanPerformer.ReadCaptchaResult onReadCaptcha(ChanPerformer.ReadCaptchaData data)
                throws ExtensionException, HttpException, InvalidResponseException;
    }

    private static class ChanCaptchaReader implements CaptchaReader {
        private final String chanName;

        public ChanCaptchaReader(String chanName) {
            this.chanName = chanName;
        }

        @Override
        public ChanPerformer.ReadCaptchaResult onReadCaptcha(ChanPerformer.ReadCaptchaData data)
                throws ExtensionException, HttpException, InvalidResponseException {
            return ChanPerformer.get(chanName).safe().onReadCaptcha(data);
        }
    }

    public ReadCaptchaTask(Callback callback, CaptchaReader captchaReader,
                           String captchaType, String requirement, String[] captchaPass, boolean mayShowLoadButton,
                           String chanName, String boardName, String threadNumber) {
        if (captchaReader == null) {
            captchaReader = new ChanCaptchaReader(chanName);
        }
        this.callback = callback;
        this.captchaReader = captchaReader;
        this.captchaType = captchaType;
        this.captchaPass = captchaPass;
        this.mayShowLoadButton = mayShowLoadButton;
        this.requirement = requirement;
        this.chanName = chanName;
        this.boardName = boardName;
        this.threadNumber = threadNumber;
    }

    @Override
    protected Boolean doInBackground(HttpHolder holder, Void... params) {
        Thread thread = Thread.currentThread();
        ChanPerformer.ReadCaptchaResult result;
        try {
            result = captchaReader.onReadCaptcha(new ChanPerformer.ReadCaptchaData(captchaType,
                    captchaPass, mayShowLoadButton, requirement, boardName, threadNumber, holder));
        } catch (ExtensionException | HttpException | InvalidResponseException e) {
            errorItem = e.getErrorItemAndHandle();
            return false;
        } finally {
            ChanConfiguration.get(chanName).commit();
        }
        captchaState = result.captchaState;
        captchaData = result.captchaData;
        loadedCaptchaType = result.captchaType;
        input = result.input;
        validity = result.validity;
        String captchaType = loadedCaptchaType != null ? loadedCaptchaType : this.captchaType;
        boolean recaptcha2 = ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_2.equals(captchaType);
        boolean recaptcha1 = ChanConfiguration.CAPTCHA_TYPE_RECAPTCHA_1.equals(captchaType);
        boolean mailru = ChanConfiguration.CAPTCHA_TYPE_MAILRU.equals(captchaType);
        if (captchaState != ChanPerformer.CaptchaState.CAPTCHA) {
            return true;
        }
        if (thread.isInterrupted()) {
            return null;
        }
        if (mayShowLoadButton && recaptcha2) {
            captchaState = ChanPerformer.CaptchaState.NEED_LOAD;
            return true;
        }
        if (recaptcha2 || recaptcha1) {
            String apiKey = captchaData.get(ChanPerformer.CaptchaData.API_KEY);
            String referer = captchaData.get(ChanPerformer.CaptchaData.REFERER);
            try {
                RecaptchaReader recaptchaReader = RecaptchaReader.getInstance();
                String challenge;
                if (recaptcha2) {
                    challenge = recaptchaReader.getChallenge2(holder, apiKey, referer,
                            Preferences.isRecaptchaJavascript());
                } else {
                    challenge = recaptchaReader.getChallenge1(holder, apiKey,
                            Preferences.isRecaptchaJavascript());
                }
                captchaData.put(ChanPerformer.CaptchaData.CHALLENGE, challenge);
                if (thread.isInterrupted()) {
                    return null;
                }
                Pair<Bitmap, Boolean> pair;
                if (recaptcha2) {
                    pair = recaptchaReader.getImage2(holder, apiKey, challenge, null, true);
                } else {
                    pair = recaptchaReader.getImage1(holder, challenge, true);
                }
                image = pair.first;
                blackAndWhite = pair.second;
                return true;
            } catch (RecaptchaReader.SkipException e) {
                captchaData.put(RECAPTCHA_SKIP_RESPONSE, e.getResponse());
                captchaState = ChanPerformer.CaptchaState.SKIP;
                return true;
            } catch (RecaptchaReader.CancelException e) {
                captchaState = ChanPerformer.CaptchaState.NEED_LOAD;
                return true;
            } catch (HttpException e) {
                errorItem = e.getErrorItemAndHandle();
                return false;
            }
        } else if (mailru) {
            CaptchaServiceReader reader = CaptchaServiceReader.getInstance();
            CaptchaServiceReader.Result captchaResult;
            try {
                if (mailru) {
                    captchaResult = reader.readMailru(holder, chanName, captchaData
                            .get(ChanPerformer.CaptchaData.API_KEY));
                } else {
                    throw new RuntimeException();
                }
            } catch (HttpException | InvalidResponseException e) {
                errorItem = e.getErrorItemAndHandle();
                return false;
            }
            captchaData.put(ChanPerformer.CaptchaData.CHALLENGE, captchaResult.challenge);
            image = captchaResult.image;
            blackAndWhite = captchaResult.blackAndWhite;
            return true;
        } else if (result.image != null) {
            Bitmap image = result.image;
            blackAndWhite = GraphicsUtils.isBlackAndWhiteCaptchaImage(image);
            this.image = blackAndWhite ? GraphicsUtils.handleBlackAndWhiteCaptchaImage(image).first : image;
            large = result.large;
            return true;
        }
        errorItem = new ErrorItem(ErrorItem.TYPE_UNKNOWN);
        return false;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (result) {
            callback.onReadCaptchaSuccess(captchaState, captchaData, loadedCaptchaType, input, validity,
                    image, large, blackAndWhite);
        } else {
            callback.onReadCaptchaError(errorItem);
        }
    }
}
