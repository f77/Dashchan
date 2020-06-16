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

package chan.http;

import android.net.Uri;
import android.util.Base64;
import android.util.Pair;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.preference.AdvancedPreferences;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

import chan.annotation.Extendable;
import chan.annotation.Public;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.content.ExtensionException;
import chan.util.StringUtils;

@Public
public final class WebSocket {
    private final Uri uri;
    private final HttpHolder holder;

    private int connectTimeout = 15000;
    private int readTimeout = 15000;

    private ArrayList<Pair<String, String>> headers;
    private CookieBuilder cookieBuilder;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean closed = false;

    private final LinkedBlockingQueue<ReadFrame> readQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<WriteFrame> writeQueue = new LinkedBlockingQueue<>();

    private final HashSet<Object> results = new HashSet<>();
    private volatile boolean cancelResults = false;

    @Public
    public static class Event {
        private final ReadFrame frame;
        private final WebSocket webSocket;

        private Event(ReadFrame frame, WebSocket webSocket) {
            this.frame = frame;
            this.webSocket = webSocket;
        }

        @Public
        public HttpResponse getResponse() {
            return new HttpResponse(frame.data);
        }

        @Public
        public boolean isBinary() {
            return frame.opcode == 2;
        }

        @Public
        public void store(String key, Object object) {
            webSocket.store(key, object);
        }

        @Public
        public <T> T get(String key) {
            return webSocket.get(key);
        }

        @Public
        public void complete(Object result) {
            webSocket.complete(result);
        }

        @Public
        public void close() {
            webSocket.closeSocket();
        }
    }

    @Extendable
    public interface EventHandler {
        @Extendable
        public void onEvent(Event event);
    }

    @Public
    public WebSocket(Uri uri, HttpHolder holder, HttpRequest.Preset preset) {
        if (holder == null && preset instanceof HttpRequest.HolderPreset) {
            holder = ((HttpRequest.HolderPreset) preset).getHolder();
        }
        if (holder == null) {
            holder = new HttpHolder();
        }
        this.uri = uri;
        this.holder = holder;
        if (preset instanceof HttpRequest.TimeoutsPreset) {
            setTimeouts(((HttpRequest.TimeoutsPreset) preset).getConnectTimeout(),
                    ((HttpRequest.TimeoutsPreset) preset).getReadTimeout());
        }
    }

    @Public
    public WebSocket(Uri uri, HttpHolder holder) {
        this(uri, holder, null);
    }

    @Public
    public WebSocket(Uri uri, HttpRequest.Preset preset) {
        this(uri, null, preset);
    }

    private WebSocket addHeader(Pair<String, String> header) {
        if (header != null) {
            if (headers == null) {
                headers = new ArrayList<>();
            }
            headers.add(header);
        }
        return this;
    }

    @Public
    public WebSocket addHeader(String name, String value) {
        return addHeader(new Pair<>(name, value));
    }

    @Public
    public WebSocket addCookie(String name, String value) {
        if (value != null) {
            if (cookieBuilder == null) {
                cookieBuilder = new CookieBuilder();
            }
            cookieBuilder.append(name, value);
        }
        return this;
    }

    @Public
    public WebSocket addCookie(String cookie) {
        if (cookie != null) {
            if (cookieBuilder == null) {
                cookieBuilder = new CookieBuilder();
            }
            cookieBuilder.append(cookie);
        }
        return this;
    }

    @Public
    public WebSocket addCookie(CookieBuilder builder) {
        if (builder != null) {
            if (cookieBuilder == null) {
                cookieBuilder = new CookieBuilder();
            }
            cookieBuilder.append(builder);
        }
        return this;
    }

    @Public
    public WebSocket setTimeouts(int connectTimeout, int readTimeout) {
        if (connectTimeout >= 0) {
            this.connectTimeout = connectTimeout;
        }
        if (readTimeout >= 0) {
            this.readTimeout = readTimeout;
        }
        return this;
    }

    private static final Random RANDOM = new Random(System.currentTimeMillis());
    private static final Pattern RESPONSE_CODE_PATTERN = Pattern.compile("HTTP/1.[10] (\\d+) (.*)");

    @Public
    public Connection open(EventHandler handler) throws HttpException {
        boolean success = false;
        try {
            if (socket != null) {
                throw new IllegalStateException("Web socket is open");
            }
            if (closed) {
                throw new HttpClient.DisconnectedIOException();
            }
            String chanName = ChanManager.getInstance().getChanNameByHost(uri.getAuthority());
            ChanLocator locator = ChanLocator.get(chanName);
            boolean verifyCertificate = locator.isUseHttps() && Preferences.isVerifyCertificate();
            holder.initRequest(uri, null, chanName, verifyCertificate, 0, 0);
            SocketResult socketResult = openSocket(uri, chanName, verifyCertificate, 5);
            socket = socketResult.socket;
            inputStream = socketResult.inputStream;
            outputStream = socketResult.outputStream;
            if (closed) {
                throw new HttpClient.DisconnectedIOException();
            }

            new Thread(() -> {
                try {
                    ArrayList<ReadFrame> frames = new ArrayList<>();
                    while (true) {
                        ReadFrame frame = ReadFrame.read(inputStream);
                        if (frame.fin) {
                            if (frames.size() > 0) {
                                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                for (ReadFrame dataFrame : frames) {
                                    byteArrayOutputStream.write(dataFrame.data);
                                }
                                byteArrayOutputStream.write(frame.data);
                                frame = new ReadFrame(frames.get(0).opcode, true, byteArrayOutputStream.toByteArray());
                                frames.clear();
                            }

                            switch (frame.opcode) {
                                case 1:
                                case 2: {
                                    readQueue.add(frame);
                                    break;
                                }
                                case 8: {
                                    int code = -1;
                                    if (frame.data.length >= 2) {
                                        code = IOUtils.bytesToInt(false, 0, 2, frame.data);
                                    }
                                    if (!closed) {
                                        connectionCloseException = true;
                                        handleException(new IOException("Connection closed by peer: " + code),
                                                false, true);
                                        closeSocket();
                                    }
                                    return;
                                }
                                case 9: {
                                    writeQueue.add(new WriteFrame(10, true, frame.data));
                                    break;
                                }
                                default: {
                                    throw new IOException("Unknown opcode: " + frame.opcode);
                                }
                            }
                        } else {
                            frames.add(frame);
                        }
                    }
                } catch (IOException e) {
                    handleException(e, true, !(e instanceof WebSocketException));
                    closeSocket();
                }
            }).start();

            new Thread(() -> {
                try {
                    while (true) {
                        ReadFrame frame = readQueue.take();
                        if (frame == END_READ_FRAME) {
                            break;
                        }
                        handler.onEvent(new Event(frame, this));
                    }
                } catch (LinkageError | RuntimeException e) {
                    ExtensionException.logException(e, false);
                    handleException(new IOException(), true, false);
                } catch (InterruptedException e) {
                    // Ignore exception
                }
                synchronized (results) {
                    cancelResults = true;
                    results.notifyAll();
                }
            }).start();

            new Thread(() -> {
                try {
                    byte[] buffer = new byte[8192];
                    while (true) {
                        WriteFrame frame = writeQueue.take();
                        if (frame == END_WRITE_FRAME) {
                            return;
                        }
                        frame.write(outputStream, buffer);
                    }
                } catch (IOException e) {
                    handleException(e, true, !(e instanceof WebSocketException));
                    closeSocket();
                } catch (InterruptedException e) {
                    // Ignore exception
                }
            }).start();

            holder.setCallback(() -> {
                try {
                    close();
                } catch (Exception e) {
                    // Ignore exception
                }
            });

            success = true;
        } catch (IOException e) {
            handleException(e, false, true);
            checkException();
        } finally {
            if (!success) {
                closeSocket();
            }
        }
        return new Connection();
    }

    private static class SocketResult {
        public final Socket socket;
        public final InputStream inputStream;
        public final OutputStream outputStream;

        public SocketResult(Socket socket, InputStream inputStream, OutputStream outputStream) {
            this.socket = socket;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }
    }

    private SocketResult openSocket(Uri uri, String chanName, boolean verifyCertificate, int attempts)
            throws HttpException, IOException {
        Socket socket = null;
        try {
            URL url = HttpClient.getInstance().encodeUri(uri);
            String scheme = uri.getScheme();
            boolean secure;
            int port = url.getPort();
            if ("https".equals(scheme) || "wss".equals(scheme)) {
                secure = true;
                if (port == -1) {
                    port = 443;
                }
            } else if ("http".equals(scheme) || "ws".equals(scheme)) {
                secure = false;
                if (port == -1) {
                    port = 80;
                }
            } else {
                throw new HttpException(ErrorItem.TYPE_UNSUPPORTED_SCHEME, false, false);
            }

            socket = SocketFactory.getDefault().createSocket();
            socket.setSoTimeout(Math.max(readTimeout, 60000));
            socket.connect(new InetSocketAddress(url.getHost(), port), connectTimeout);
            if (secure) {
                SSLSocket sslSocket = (SSLSocket) HttpClient.getInstance().getSSLSocketFactory(verifyCertificate)
                        .createSocket(socket, url.getHost(), port, true);
                socket = sslSocket;
                sslSocket.startHandshake();
                if (!HttpClient.getInstance().getHostnameVerifier(verifyCertificate)
                        .verify(uri.getHost(), sslSocket.getSession())) {
                    throw new HttpException(ErrorItem.TYPE_INVALID_CERTIFICATE, false, false);
                }
            }

            byte[] webSocketKey = new byte[16];
            for (int i = 0; i < webSocketKey.length; i++) {
                webSocketKey[i] = (byte) RANDOM.nextInt(256);
            }
            String webSocketKeyEncoded = Base64.encodeToString(webSocketKey, Base64.NO_WRAP);

            InputStream inputStream = new BufferedInputStream(socket.getInputStream());
            OutputStream outputStream = new BufferedOutputStream(socket.getOutputStream());
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append("GET ").append(url.getFile()).append(" HTTP/1.1\r\n");
            boolean addHost = true;
            boolean addOrigin = true;
            boolean addUserAgent = true;
            if (headers != null) {
                for (Pair<String, String> header : headers) {
                    switch (header.first.toLowerCase(Locale.US)) {
                        case "host": {
                            addHost = false;
                            break;
                        }
                        case "origin": {
                            addOrigin = false;
                            break;
                        }
                        case "user-agent": {
                            addUserAgent = false;
                            break;
                        }
                        case "connection":
                        case "upgrade":
                        case "sec-websocket-version":
                        case "sec-websocket-key":
                        case "sec-websocket-extensions":
                        case "sec-websocket-protocol": {
                            // Ignore headers
                            continue;
                        }
                    }
                    requestBuilder.append(header.first).append(": ").append(header.second.replaceAll("[\r\n]", ""))
                            .append("\r\n");
                }
            }
            boolean appendPort = !(port == 80 && !secure || port == 443 && secure);
            if (addHost) {
                requestBuilder.append("Host: ").append(url.getHost());
                if (appendPort) {
                    requestBuilder.append(':').append(port);
                }
                requestBuilder.append("\r\n");
            }
            if (addOrigin) {
                requestBuilder.append("Origin: ").append(scheme.replace("ws", "http"))
                        .append("://").append(url.getHost());
                if (appendPort) {
                    requestBuilder.append(':').append(port);
                }
                requestBuilder.append("\r\n");
            }
            requestBuilder.append("Connection: Upgrade\r\n");
            requestBuilder.append("Upgrade: websocket\r\n");
            requestBuilder.append("Sec-WebSocket-Version: 13\r\n");
            requestBuilder.append("Sec-WebSocket-Key: ").append(webSocketKeyEncoded).append("\r\n");
            requestBuilder.append("Sec-WebSocket-Protocol: chat, superchat\r\n");
            CookieBuilder cookieBuilder = HttpClient.getInstance()
                    .obtainModifiedCookieBuilder(this.cookieBuilder, chanName);
            if (cookieBuilder != null) {
                requestBuilder.append("Cookie: ").append(cookieBuilder.build().replaceAll("[\r\n]", ""))
                        .append("\r\n");
            }
            if (addUserAgent) {
                requestBuilder.append("User-Agent: ").append(AdvancedPreferences.getUserAgent(chanName)
                        .replaceAll("[\r\n]", "")).append("\r\n");
            }
            requestBuilder.append("\r\n");
            outputStream.write(requestBuilder.toString().getBytes("ISO-8859-1"));
            outputStream.flush();

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int endCount = 0;
            while (true) {
                int b = inputStream.read();
                if (b == -1) {
                    throw new HttpException(ErrorItem.TYPE_CONNECTION_RESET, false, true);
                }
                byteArrayOutputStream.write(b);
                switch (b) {
                    case '\r': {
                        if (endCount == 0 || endCount == 2) {
                            endCount++;
                        }
                        break;
                    }
                    case '\n': {
                        if (endCount == 1 || endCount == 3) {
                            endCount++;
                        }
                        break;
                    }
                    default: {
                        endCount = 0;
                        break;
                    }
                }
                if (endCount == 4) {
                    break;
                }
            }

            String[] responseHeaders = new String(byteArrayOutputStream.toByteArray(), "ISO-8859-1").split("\r\n");
            Matcher matcher = RESPONSE_CODE_PATTERN.matcher(responseHeaders[0]);
            if (matcher.matches()) {
                int responseCode = Integer.parseInt(matcher.group(1));
                switch (responseCode) {
                    case HttpURLConnection.HTTP_MOVED_PERM:
                    case HttpURLConnection.HTTP_MOVED_TEMP:
                    case HttpURLConnection.HTTP_SEE_OTHER:
                    case HttpClient.HTTP_TEMPORARY_REDIRECT: {
                        if (attempts > 0) {
                            for (String header : responseHeaders) {
                                if (header.toLowerCase(Locale.US).startsWith("location:")) {
                                    Uri redirectedUri = HttpClient.getInstance().obtainRedirectedUri(uri,
                                            header.substring(header.indexOf(':') + 1).trim());
                                    closeSocket(socket);
                                    socket = null;
                                    scheme = redirectedUri.getScheme();
                                    boolean newSecure = "https".equals(scheme) || "wss".equals(scheme);
                                    if (holder.verifyCertificate && secure && !newSecure) {
                                        // Redirect from https/wss to http/ws is unsafe
                                        throw new HttpException(ErrorItem.TYPE_UNSAFE_REDIRECT, true, false);
                                    }
                                    return openSocket(redirectedUri, chanName, verifyCertificate, attempts - 1);
                                }
                            }
                        }
                        break;
                    }
                }
                String responseText = matcher.group(2);
                if (responseCode != 101) {
                    throw new HttpException(responseCode, responseText);
                }
            } else {
                throw new HttpException(ErrorItem.TYPE_INVALID_RESPONSE, false, false);
            }

            String checkKeyEncoded;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                byte[] result = digest.digest((webSocketKeyEncoded + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                        .getBytes("ISO-8859-1"));
                checkKeyEncoded = Base64.encodeToString(result, Base64.NO_WRAP);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            boolean verified = false;
            for (String header : responseHeaders) {
                if (header.toLowerCase(Locale.US).startsWith("sec-websocket-accept:")) {
                    String value = header.substring(header.indexOf(':') + 1).trim();
                    if (value.equals(checkKeyEncoded)) {
                        verified = true;
                        break;
                    }
                }
            }
            if (!verified) {
                throw new HttpException(0, "Not verified");
            }

            try {
                return new SocketResult(socket, inputStream, outputStream);
            } finally {
                socket = null;
            }
        } finally {
            closeSocket(socket);
        }
    }

    private void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore exception
            }
        }
    }

    private void closeSocket() {
        Socket socket = this.socket;
        closed = true;
        this.socket = null;
        if (socket != null) {
            writeQueue.add(END_WRITE_FRAME);
            readQueue.add(END_READ_FRAME);
            IOUtils.close(inputStream);
            IOUtils.close(outputStream);
            closeSocket(socket);
        }
    }

    private volatile boolean logException = false;
    private volatile boolean connectionCloseException = false;
    private volatile IOException exception;

    private void handleException(IOException exception, boolean checkSocket, boolean logException) {
        if ((!checkSocket || socket != null) && this.exception == null) {
            this.logException = logException;
            this.exception = exception;
        }
    }

    private void checkException() throws HttpException {
        boolean handled = true;
        try {
            IOException exception = this.exception;
            if (exception != null) {
                if (logException) {
                    // Check and log only if exception occured when socket was open
                    HttpClient.getInstance().checkExceptionAndThrow(exception);
                }
                throw new HttpException(ErrorItem.TYPE_DOWNLOAD, false, true, exception);
            }
            try {
                holder.checkDisconnected();
            } catch (HttpClient.DisconnectedIOException e) {
                throw new HttpException(0, false, false, e);
            }
            handled = false;
        } finally {
            if (handled) {
                closeSocket();
            }
        }
    }

    @Public
    public static class ComplexBinaryBuilder {
        private final Connection connection;
        private final ArrayList<InputStream> writeData = new ArrayList<>();
        private int length;

        private ComplexBinaryBuilder(Connection connection) {
            this.connection = connection;
        }

        @Public
        public ComplexBinaryBuilder bytes(byte... bytes) {
            if (bytes != null && bytes.length > 0) {
                writeData.add(new SimpleByteArrayInputStream(bytes));
                length += bytes.length;
            }
            return this;
        }

        @Public
        public ComplexBinaryBuilder bytes(int... bytes) {
            if (bytes != null && bytes.length > 0) {
                byte[] next = new byte[bytes.length];
                for (int i = 0; i < bytes.length; i++) {
                    next[i] = (byte) bytes[i];
                }
                return bytes(next);
            }
            return this;
        }

        @Public
        public ComplexBinaryBuilder string(String string) {
            if (!StringUtils.isEmpty(string)) {
                bytes(string.getBytes());
            }
            return this;
        }

        @Public
        public ComplexBinaryBuilder stream(InputStream inputStream, int count) {
            if (inputStream != null && count > 0) {
                writeData.add(new LimitedInputStream(inputStream, count));
                length += count;
            }
            return this;
        }

        @Public
        public ComplexBinaryBuilder wrap(Wrapper wrapper) {
            return wrapper.apply(this);
        }

        @Public
        public Connection send() throws HttpException {
            return connection.sendBuiltComplexBinary(this);
        }

        @Extendable
        public interface Wrapper {
            @Extendable
            public ComplexBinaryBuilder apply(ComplexBinaryBuilder builder);
        }
    }

    @Public
    public class Connection {
        @Public
        public Connection sendText(String text) throws HttpException {
            checkException();
            try {
                writeQueue.add(new WriteFrame(1, true, text != null ? text.getBytes("UTF-8") : new byte[0]));
            } catch (UnsupportedEncodingException e) {
                checkException();
                throw new RuntimeException(e);
            }
            return this;
        }

        @Public
        public Connection sendBinary(byte[] data) throws HttpException {
            checkException();
            writeQueue.add(new WriteFrame(2, true, data));
            return this;
        }

        @Public
        public ComplexBinaryBuilder sendComplexBinary() throws HttpException {
            checkException();
            return new ComplexBinaryBuilder(this);
        }

        private Connection sendBuiltComplexBinary(ComplexBinaryBuilder builder) throws HttpException {
            checkException();
            writeQueue.add(new WriteFrame(2, true, builder.writeData, builder.length));
            return this;
        }

        @Public
        public Connection await(Object... results) throws HttpException {
            if (results == null || results.length == 0) {
                return this;
            }
            synchronized (WebSocket.this.results) {
                try {
                    OUTER:
                    while (!cancelResults) {
                        for (Object result : results) {
                            if (WebSocket.this.results.remove(result)) {
                                break OUTER;
                            }
                        }
                        WebSocket.this.results.wait();
                    }
                } catch (InterruptedException e) {
                    throw new HttpException(0, false, false, e);
                }
            }
            try {
                checkException();
            } catch (HttpException e) {
                if (!connectionCloseException) {
                    throw e;
                }
            }
            return this;
        }

        @Public
        public Connection store(String key, Object data) {
            WebSocket.this.store(key, data);
            return this;
        }

        @Public
        public <T> T get(String key) {
            return WebSocket.this.get(key);
        }

        @Public
        public Result close() throws HttpException {
            checkException();
            closeSocket();
            return new Result();
        }
    }

    @Public
    public class Result {
        @Public
        public <T> T get(String key) {
            return WebSocket.this.get(key);
        }
    }

    private WebSocket close() throws HttpException {
        checkException();
        closeSocket();
        return this;
    }

    private void complete(Object result) {
        synchronized (results) {
            results.add(result);
            results.notifyAll();
        }
    }

    private static final class WebSocketException extends IOException {
    }

    private static void checkReadByte(int readByte) throws WebSocketException {
        if (readByte == -1) {
            throw new WebSocketException();
        }
    }

    private static final ReadFrame END_READ_FRAME = new ReadFrame(0, true, null);
    private static final WriteFrame END_WRITE_FRAME = new WriteFrame(0, true, null);

    private static class ReadFrame {
        public final int opcode;
        public final boolean fin;
        public final byte[] data;

        public ReadFrame(int opcode, boolean fin, byte[] data) {
            this.opcode = opcode;
            this.fin = fin;
            this.data = data;
        }

        public static ReadFrame read(InputStream inputStream) throws IOException {
            int opcodeData = inputStream.read();
            checkReadByte(opcodeData);
            int length = inputStream.read();
            checkReadByte(length);
            boolean fin = (opcodeData & 0x80) == 0x80;
            int opcode = opcodeData & 0x0f;
            boolean masked = (length & 0x80) == 0x80;

            length = length & 0x7f;
            if (length >= 126) {
                byte[] data = new byte[length == 126 ? 2 : 8];
                if (!IOUtils.readExactlyCheck(inputStream, data, 0, data.length)) {
                    checkReadByte(-1);
                }
                if (data.length == 8 && (data[0] != 0 || data[1] != 0 || data[2] != 0 || data[3] != 0 ||
                        (data[4] & 0x80) == 0x80)) {
                    // Too large frame
                    checkReadByte(-1);
                }
                length = IOUtils.bytesToInt(false, 0, data.length, data);
            }

            byte[] mask = null;
            if (masked) {
                mask = new byte[4];
                if (!IOUtils.readExactlyCheck(inputStream, mask, 0, mask.length)) {
                    checkReadByte(-1);
                }
            }

            if (length < 0) {
                checkReadByte(-1);
            }

            byte[] data = new byte[length];
            if (!IOUtils.readExactlyCheck(inputStream, data, 0, data.length)) {
                checkReadByte(-1);
            }

            if (mask != null) {
                for (int i = 0; i < data.length; i++) {
                    data[i] ^= mask[i % mask.length];
                }
            }

            return new ReadFrame(opcode, fin, data);
        }
    }

    private static class WriteFrame {
        public final int opcode;
        public final boolean fin;
        public final List<InputStream> inputStreams;
        public final int length;

        public WriteFrame(int opcode, boolean fin, byte[] data) {
            this(opcode, fin, data != null ? Collections.singletonList(new SimpleByteArrayInputStream(data))
                    : Collections.emptyList(), data != null ? data.length : 0);
        }

        public WriteFrame(int opcode, boolean fin, List<InputStream> inputStream, int length) {
            this.opcode = opcode;
            this.fin = fin;
            this.inputStreams = inputStream;
            this.length = length;
        }

        public void write(OutputStream outputStream, byte[] buffer) throws IOException {
            outputStream.write(0x80 | opcode);

            if (length >= 126) {
                if (length >= 0x10000) {
                    outputStream.write(127 | 0x80);
                    outputStream.write(IOUtils.intToBytes(length, false, 0, 8, null));
                } else {
                    outputStream.write(126 | 0x80);
                    outputStream.write(IOUtils.intToBytes(length, false, 0, 2, null));
                }
            } else {
                outputStream.write(length | 0x80);
            }

            byte[] mask = new byte[4];
            for (int i = 0; i < mask.length; i++) {
                mask[i] = (byte) RANDOM.nextInt(256);
            }
            outputStream.write(mask);

            int streamIndex = 0;
            int index = 0;
            while (index < length && streamIndex < inputStreams.size()) {
                InputStream inputStream = inputStreams.get(streamIndex);
                int count = inputStream.read(buffer);
                if (count < 0) {
                    streamIndex++;
                    continue;
                }
                for (int i = 0; i < count; i++) {
                    buffer[i] ^= mask[(index + i) % mask.length];
                }
                outputStream.write(buffer, 0, count);
                index += count;
            }

            outputStream.flush();
        }
    }

    private static class SimpleByteArrayInputStream extends InputStream {
        private final byte[] array;

        private int position = 0;

        public SimpleByteArrayInputStream(byte[] array) {
            this.array = array;
        }

        @Override
        public int read() throws IOException {
            return position < array.length ? array[position++] & 0xff : -1;
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return read(buffer, 0, buffer.length);
        }

        @Override
        public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            int left = array.length - position;
            if (left > 0) {
                byteCount = Math.min(byteCount, left);
                System.arraycopy(array, position, buffer, byteOffset, byteCount);
                position += byteCount;
                return byteCount;
            } else {
                return -1;
            }
        }
    }

    private static class LimitedInputStream extends InputStream {
        private final InputStream inputStream;
        private final int count;

        private int position;

        private LimitedInputStream(InputStream inputStream, int count) {
            this.inputStream = inputStream;
            this.count = count;
        }

        @Override
        public int read() throws IOException {
            if (position < count) {
                int result = inputStream.read();
                if (result >= 0) {
                    position++;
                }
                return result;
            } else {
                return -1;
            }
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return read(buffer, 0, buffer.length);
        }

        @Override
        public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            int left = count - position;
            if (left > 0) {
                byteCount = Math.min(byteCount, left);
                byteCount = inputStream.read(buffer, byteOffset, byteCount);
                if (byteCount > 0) {
                    position += byteCount;
                }
                return byteCount;
            } else {
                return -1;
            }
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }

    private final HashMap<String, Object> storedData = new HashMap<>();

    private WebSocket store(String key, Object data) {
        synchronized (storedData) {
            storedData.put(key, data);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String key) {
        synchronized (storedData) {
            return (T) storedData.get(key);
        }
    }
}
