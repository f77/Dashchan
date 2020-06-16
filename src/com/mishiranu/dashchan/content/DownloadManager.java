/*
 * Copyright 2014-2018 Fukurou Mishiranu
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

package com.mishiranu.dashchan.content;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.MimeTypes;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.util.StringUtils;

public class DownloadManager {
    private static final DownloadManager INSTANCE = new DownloadManager();

    public static DownloadManager getInstance() {
        return INSTANCE;
    }

    private DownloadManager() {
    }

    private final HashSet<String> queuedFiles = new HashSet<>();
    private final Semaphore queuePause = new Semaphore(1);

    public String getFileGlobalQueueSetName(File file) {
        return file.getAbsolutePath().toLowerCase(Locale.getDefault());
    }

    public void notifyFileAddedToDownloadQueue(File file) {
        queuedFiles.add(getFileGlobalQueueSetName(file));
    }

    public void notifyFileRemovedFromDownloadQueue(File file) {
        queuedFiles.remove(getFileGlobalQueueSetName(file));
    }

    public void notifyServiceDestroy() {
        queuedFiles.clear();
        releaseQueuePauseLock();
    }

    public void notifyFinishDownloadingInThread() {
        if (aquireQueuePauseLock(false)) {
            releaseQueuePauseLock();
        }
    }

    private boolean aquireQueuePauseLock(boolean tryOnly) {
        if (tryOnly) {
            return queuePause.tryAcquire();
        } else {
            try {
                queuePause.acquire();
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void releaseQueuePauseLock() {
        queuePause.release();
    }

    public static class RequestItem {
        public final Uri uri;
        public final String fileName;
        public final String originalName;

        public RequestItem(Uri uri, String fileName, String originalName) {
            this.uri = uri;
            this.fileName = fileName;
            this.originalName = originalName;
        }
    }

    private static class DialogDirectory implements Comparable<DialogDirectory> {
        public final List<String> segments;
        public long lastModified;

        public DialogDirectory(File root, File directory) {
            String rootPath = root.getAbsolutePath();
            String directoryPath = directory.getAbsolutePath();
            String relativePath = directoryPath.substring(rootPath.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            segments = Arrays.asList(relativePath.split("/"));
            this.lastModified = directory.lastModified();
        }

        public boolean filter(String name) {
            Locale locale = Locale.getDefault();
            name = name.toLowerCase(locale);
            String lastSegment = segments.get(segments.size() - 1).toLowerCase(locale);
            if (lastSegment.startsWith(name)) {
                return true;
            }
            String[] splitted = lastSegment.split("[\\W_]+");
            for (String part : splitted) {
                if (part.startsWith(name)) {
                    return true;
                }
            }
            return false;
        }

        private String convert(boolean displayName) {
            StringBuilder builder = new StringBuilder();
            for (String segment : segments) {
                if (builder.length() > 0) {
                    if (displayName) {
                        builder.append(" / ");
                    } else {
                        builder.append('/');
                    }
                }
                builder.append(segment);
            }
            if (!displayName) {
                builder.append('/');
            }
            return builder.toString();
        }

        public String getDisplayName() {
            return convert(true);
        }

        @Override
        public String toString() {
            return convert(false);
        }

        @Override
        public int compareTo(DialogDirectory another) {
            return ((Long) another.lastModified).compareTo(lastModified);
        }
    }

    private static class DialogAdapter extends BaseAdapter implements Filterable {
        private final File root;
        private final Runnable refresh;

        private List<DialogDirectory> items = Collections.emptyList();

        public DialogAdapter(File root, Runnable refresh) {
            this.root = root;
            this.refresh = refresh;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public DialogDirectory getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DialogDirectory dialogDirectory = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(android.R.layout
                        .simple_spinner_dropdown_item, parent, false);
                ((TextView) convertView).setEllipsize(TextUtils.TruncateAt.START);
            }
            ((TextView) convertView).setText(dialogDirectory.getDisplayName());
            return convertView;
        }

        private final Object lastDirectoryLock = new Object();
        private boolean lastDirectoryCancel = false;
        private String lastDirectoryPath;
        private List<DialogDirectory> lastDirectoryItems;
        private AsyncTask<Void, Void, List<DialogDirectory>> lastDirectoryTask;

        private final Filter filter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String constraintString = constraint.toString();
                int separatorIndex = constraintString.lastIndexOf('/');
                String directoryPath = separatorIndex >= 0 ? constraintString.substring(0, separatorIndex) : "";
                File directory = StringUtils.isEmpty(directoryPath) ? root : new File(root, directoryPath);

                List<DialogDirectory> items;
                synchronized (lastDirectoryLock) {
                    if (lastDirectoryPath == null || !StringUtils.equals(lastDirectoryPath, directoryPath)) {
                        lastDirectoryPath = directoryPath;
                        lastDirectoryItems = Collections.emptyList();

                        if (lastDirectoryTask != null) {
                            lastDirectoryTask.cancel(true);
                            lastDirectoryTask = null;
                        }

                        if (!lastDirectoryCancel) {
                            lastDirectoryTask = new AsyncTask<Void, Void, List<DialogDirectory>>() {
                                @Override
                                protected List<DialogDirectory> doInBackground(Void... params) {
                                    ArrayList<DialogDirectory> items = new ArrayList<>();
                                    File[] files = directory.listFiles();
                                    if (files != null) {
                                        for (File file : files) {
                                            if (isCancelled()) {
                                                break;
                                            }
                                            if (file.isDirectory()) {
                                                items.add(new DialogDirectory(root, file));
                                            }
                                        }
                                    }
                                    if (!isCancelled()) {
                                        Collections.sort(items);
                                    }
                                    return items;
                                }

                                @Override
                                protected void onPostExecute(List<DialogDirectory> items) {
                                    synchronized (lastDirectoryLock) {
                                        lastDirectoryItems = items;
                                        lastDirectoryTask = null;
                                        notifyDataSetChanged();
                                        refresh.run();
                                    }
                                }
                            };
                            lastDirectoryTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        }
                    }

                    items = lastDirectoryItems;
                }

                String name = constraintString.substring(separatorIndex + 1);
                ArrayList<DialogDirectory> result = new ArrayList<>();
                for (DialogDirectory item : items) {
                    if (item.filter(name)) {
                        result.add(item);
                    }
                }

                FilterResults results = new FilterResults();
                results.values = result;
                results.count = result.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                @SuppressWarnings("unchecked")
                ArrayList<DialogDirectory> items = (ArrayList<DialogDirectory>) results.values;
                DialogAdapter.this.items = items;
                notifyDataSetChanged();
            }
        };

        @Override
        public Filter getFilter() {
            return filter;
        }

        public void shutdown() {
            synchronized (lastDirectoryLock) {
                lastDirectoryCancel = true;
                if (lastDirectoryTask != null) {
                    lastDirectoryTask.cancel(true);
                    lastDirectoryTask = null;
                }
            }
        }
    }

    private class DownloadDialog implements DialogInterface.OnClickListener, RadioGroup.OnCheckedChangeListener,
            AutoCompleteTextView.OnEditorActionListener, AdapterView.OnItemClickListener {
        private final Context context;
        private final DialogCallback callback;
        private final String chanName;
        private final String boardName;
        private final String threadNumber;

        private final AlertDialog dialog;
        private final DialogAdapter adapter;
        private final CheckBox detailNameCheckBox;
        private final CheckBox originalNameCheckBox;
        private final AutoCompleteTextView editText;
        private final InputMethodManager inputMethodManager;

        private final Runnable dropDownRunnable;

        @SuppressLint("InflateParams")
        public DownloadDialog(Context context, DialogCallback callback, String chanName,
                              String boardName, String threadNumber, String threadTitle,
                              boolean allowDetailedFileName, boolean allowOriginalName) {
            File root = Preferences.getDownloadDirectory();
            this.context = context;
            this.callback = callback;
            this.chanName = chanName;
            this.boardName = boardName;
            this.threadNumber = threadNumber;
            inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);

            View view = LayoutInflater.from(context).inflate(R.layout.dialog_download_choice, null);
            RadioGroup radioGroup = view.findViewById(R.id.download_choice);
            radioGroup.check(R.id.download_common);
            radioGroup.setOnCheckedChangeListener(this);
            ((RadioButton) view.findViewById(R.id.download_common)).setText(context
                    .getString(R.string.text_download_to_format, root.getName()));

            detailNameCheckBox = view.findViewById(R.id.download_detail_name);
            originalNameCheckBox = view.findViewById(R.id.download_original_name);
            if (chanName == null && boardName == null && threadNumber == null) {
                allowDetailedFileName = false;
            }
            if (allowDetailedFileName) {
                detailNameCheckBox.setChecked(Preferences.isDownloadDetailName());
            } else {
                detailNameCheckBox.setVisibility(View.GONE);
            }
            if (allowOriginalName) {
                originalNameCheckBox.setChecked(Preferences.isDownloadOriginalName());
            } else {
                originalNameCheckBox.setVisibility(View.GONE);
            }

            AutoCompleteTextView editText = view.findViewById(android.R.id.text1);
            if (!allowDetailedFileName && !allowOriginalName) {
                ((ViewGroup.MarginLayoutParams) editText.getLayoutParams()).topMargin = 0;
            }

            if (threadNumber != null) {
                String chanTitle = ChanConfiguration.get(chanName).getTitle();
                if (threadTitle != null) {
                    threadTitle = StringUtils.escapeFile(StringUtils.cutIfLongerToLine(threadTitle, 50, false), false);
                }
                String text = Preferences.getSubdir(chanName, chanTitle, boardName, threadNumber, threadTitle);
                editText.setText(text);
                editText.setSelection(text.length());
                if (StringUtils.isEmpty(text)) {
                    text = Preferences.formatSubdir(Preferences.DEFAULT_SUBDIR_PATTERN, chanName, chanTitle, boardName,
                            threadNumber, threadTitle);
                }
                editText.setHint(text);
            }

            editText.setEnabled(false);
            editText.setOnEditorActionListener(this);
            editText.setOnItemClickListener(this);
            this.editText = editText;
            dropDownRunnable = this.editText::showDropDown;

            adapter = new DialogAdapter(root, () -> {
                if (DownloadDialog.this.editText.isEnabled()) {
                    refreshDropDownContents();
                }
            });
            editText.setAdapter(adapter);

            dialog = new AlertDialog.Builder(context).setTitle(R.string.text_download_title).setView(view)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, this).create();
            dialog.setOnDismissListener(d -> adapter.shutdown());
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.show();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            complete();
        }

        private void refreshDropDownContents() {
            Editable editable = editText.getEditableText();
            TextWatcher[] watchers = editable.getSpans(0, editable.length(), TextWatcher.class);
            if (watchers != null) {
                for (TextWatcher watcher : watchers) {
                    watcher.beforeTextChanged(editable, 0, 0, 0);
                    watcher.onTextChanged(editable, 0, 0, 0);
                    watcher.afterTextChanged(editable);
                }
            }
        }

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            boolean enabled = checkedId == R.id.download_subdirectory;
            editText.setEnabled(enabled);
            editText.setCompoundDrawables(null, null, enabled ? ResourceUtils.getDrawable(context,
                    R.attr.buttonCancel, 0) : null, null);
            if (enabled) {
                editText.dismissDropDown();
                refreshDropDownContents();
                if (inputMethodManager != null) {
                    inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                    editText.postDelayed(dropDownRunnable, 250);
                } else {
                    dropDownRunnable.run();
                }
            } else {
                editText.removeCallbacks(dropDownRunnable);
            }
        }

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            dialog.dismiss();
            complete();
            return true;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            editText.post(() -> {
                refreshDropDownContents();
                editText.showDropDown();
            });
        }

        private void complete() {
            String path = editText.isEnabled() ? StringUtils.nullIfEmpty(StringUtils
                    .escapeFile(editText.getText().toString(), true).trim()) : null;
            callback.onDirectoryChosen(context, path, detailNameCheckBox.isChecked(),
                    originalNameCheckBox.isChecked(), chanName, boardName, threadNumber);
        }
    }

    private interface ReplaceCallback {
        public void onConfirmReplacement(Context context, ArrayList<DownloadService.DownloadItem> downloadItems);
    }

    private void confirmReplacement(final Context context, final File directory,
                                    final ArrayList<DownloadService.DownloadItem> downloadItems, final ReplaceCallback callback) {
        ArrayList<DownloadService.DownloadItem> availableItems = new ArrayList<>();
        int queued = 0, exists = 0;
        File lastExistedFile = null;
        {
            HashSet<String> futureQueuedFiles = new HashSet<>();
            for (DownloadService.DownloadItem downloadItem : downloadItems) {
                File file = new File(directory, downloadItem.name);
                String name = getFileGlobalQueueSetName(file);
                if (queuedFiles.contains(name) || futureQueuedFiles.contains(name)) {
                    queued++;
                } else if (file.exists()) {
                    exists++;
                    lastExistedFile = file;
                } else {
                    futureQueuedFiles.add(name);
                    availableItems.add(downloadItem);
                }
            }
        }
        if (availableItems.size() != downloadItems.size()) {
            // Pause downloading until user choose the way
            aquireQueuePauseLock(true);
            int count = queued + exists;
            float density = ResourceUtils.obtainDensity(context);
            int padding = context.getResources().getDimensionPixelSize(R.dimen.dialog_padding_view);
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setPadding(padding, padding, padding, C.API_LOLLIPOP ? (int) (8f * density) : padding);
            TextView textView = new TextView(context, null, android.R.attr.textAppearanceListItem);
            textView.setText(context.getResources().getQuantityString(R.plurals.text_files_exist_format, count, count));
            linearLayout.addView(textView);
            final RadioGroup radioGroup = new RadioGroup(context);
            radioGroup.setOrientation(RadioGroup.VERTICAL);
            int[] options = {R.string.text_replace, R.string.text_keep_all, R.string.text_dont_replace};
            int[] ids = {android.R.id.button1, android.R.id.button2, android.R.id.button3};
            for (int i = 0; i < options.length; i++) {
                RadioButton radioButton = new RadioButton(context);
                radioButton.setText(options[i]);
                radioButton.setId(ids[i]);
                radioGroup.addView(radioButton);
            }
            radioGroup.check(ids[0]);
            radioGroup.setPadding(0, (int) (12f * density), 0, 0);
            linearLayout.addView(radioGroup);
            AlertDialog.Builder builder = new AlertDialog.Builder(context).setView(linearLayout)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        switch (radioGroup.getCheckedRadioButtonId()) {
                            case android.R.id.button1: {
                                callback.onConfirmReplacement(context, downloadItems);
                                break;
                            }
                            case android.R.id.button2: {
                                HashSet<String> futureQueuedFiles = new HashSet<>();
                                ArrayList<DownloadService.DownloadItem> finalItems = new ArrayList<>(downloadItems.size());
                                for (DownloadService.DownloadItem downloadItem : downloadItems) {
                                    if (availableItems.contains(downloadItem)) {
                                        futureQueuedFiles.add(getFileGlobalQueueSetName(new File(directory,
                                                downloadItem.name)));
                                        finalItems.add(downloadItem);
                                    } else {
                                        String fileName = downloadItem.name;
                                        String dotExtension = "." + StringUtils.getFileExtension(fileName);
                                        fileName = fileName.substring(0, fileName.length() - dotExtension.length());
                                        File file;
                                        String name;
                                        int i = 0;
                                        do {
                                            file = new File(directory, fileName + (i > 0 ? "-" + i : "") + dotExtension);
                                            name = getFileGlobalQueueSetName(file);
                                            i++;
                                        } while (file.exists() || queuedFiles.contains(name) ||
                                                futureQueuedFiles.contains(name));
                                        futureQueuedFiles.add(name);
                                        finalItems.add(new DownloadService.DownloadItem(downloadItem.chanName,
                                                downloadItem.uri, file.getName()));
                                    }
                                }
                                callback.onConfirmReplacement(context, finalItems);
                                break;
                            }
                            case android.R.id.button3: {
                                callback.onConfirmReplacement(context, availableItems);
                                break;
                            }
                        }
                    }).setNegativeButton(android.R.string.cancel, null);
            AlertDialog dialog;
            if (exists == 1) {
                builder.setNeutralButton(R.string.action_view, null);
                dialog = builder.create();
                final File singleFile = lastExistedFile;
                dialog.setOnShowListener(d -> {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                        String extension = StringUtils.getFileExtension(singleFile.getPath());
                        String type = MimeTypes.forExtension(extension, "image/jpeg");
                        try {
                            Uri uri = FileProvider.convertDownloadsFile(singleFile, type);
                            int intentFlags = FileProvider.getIntentFlags();
                            context.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                                    .setFlags(intentFlags | Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (ActivityNotFoundException e) {
                            ToastUtils.show(context, R.string.message_unknown_address);
                        }
                    });
                });
            } else {
                dialog = builder.create();
            }
            dialog.setOnDismissListener(d -> releaseQueuePauseLock());
            dialog.show();
        } else {
            callback.onConfirmReplacement(context, downloadItems);
        }
    }

    private interface DialogCallback {
        public void onDirectoryChosen(Context context, String path, boolean detailName, boolean originalName,
                                      String chanName, String boardName, String threadNumber);
    }

    private class StorageDialogCallback implements DialogCallback, ReplaceCallback {
        private final ArrayList<RequestItem> requestItems;
        private File directory;

        public StorageDialogCallback(ArrayList<RequestItem> requestItems) {
            this.requestItems = requestItems;
        }

        @Override
        public void onDirectoryChosen(Context context, String path, boolean detailName, boolean originalName,
                                      String chanName, String boardName, String threadNumber) {
            directory = getDownloadDirectory(path);
            ArrayList<DownloadService.DownloadItem> downloadItems = new ArrayList<>(requestItems.size());
            for (RequestItem requestItem : requestItems) {
                downloadItems.add(new DownloadService.DownloadItem(chanName, requestItem.uri,
                        getDesiredFileName(requestItem.uri, requestItem.fileName, originalName
                                ? requestItem.originalName : null, detailName, chanName, boardName, threadNumber)));
            }
            confirmReplacement(context, directory, downloadItems, this);
        }

        @Override
        public void onConfirmReplacement(Context context, ArrayList<DownloadService.DownloadItem> downloadItems) {
            DownloadService.downloadDirect(context, directory, downloadItems);
        }
    }

    private class StreamDialogCallback implements DialogCallback, ReplaceCallback {
        private final InputStream input;
        private final String fileName;
        private File directory;

        public StreamDialogCallback(InputStream input, String fileName) {
            this.input = input;
            this.fileName = fileName;
        }

        @Override
        public void onDirectoryChosen(Context context, String path, boolean detailName, boolean originalName,
                                      String chanName, String boardName, String threadNumber) {
            String fileName = this.fileName;
            if (detailName) {
                fileName = getFileNameWithChanBoardThreadData(fileName, chanName, boardName, threadNumber);
            }
            directory = getDownloadDirectory(path);
            ArrayList<DownloadService.DownloadItem> downloadItems = new ArrayList<>(1);
            downloadItems.add(new DownloadService.DownloadItem(chanName, null, fileName));
            confirmReplacement(context, directory, downloadItems, this);
        }

        @Override
        public void onConfirmReplacement(Context context, ArrayList<DownloadService.DownloadItem> downloadItems) {
            if (downloadItems.size() == 0) {
                return;
            }
            File file = new File(directory, downloadItems.get(0).name);
            OutputStream output = null;
            boolean success = false;
            try {
                output = IOUtils.openOutputStream(context, file);
                IOUtils.copyStream(input, output);
                success = true;
            } catch (IOException e) {
                Log.persistent().stack(e);
            } finally {
                IOUtils.close(input);
                IOUtils.close(output);
            }
            DownloadService.showFake(context, file, success);
        }
    }

    private File getDownloadDirectory(String path) {
        File directory = Preferences.getDownloadDirectory();
        if (path != null) {
            directory = new File(directory, path);
            directory.mkdirs();
        }
        return directory;
    }

    private String getFileNameWithChanBoardThreadData(String fileName, String chanName, String boardName,
                                                      String threadNumber) {
        String extension = StringUtils.getFileExtension(fileName);
        fileName = fileName.substring(0, fileName.length() - extension.length() - 1);
        StringBuilder builder = new StringBuilder();
        builder.append(fileName);
        if (chanName != null) {
            builder.append('-').append(chanName);
        }
        if (boardName != null) {
            builder.append('-').append(boardName);
        }
        if (threadNumber != null) {
            builder.append('-').append(threadNumber);
        }
        return builder.append('.').append(extension).toString();
    }

    private boolean isFileNameModifyingAllowed(String chanName, Uri uri) {
        return chanName != null && ChanLocator.get(chanName).safe(false).isAttachmentUri(uri);
    }

    private String getDesiredFileName(Uri uri, String fileName, String originalName, boolean detailName,
                                      String chanName, String boardName, String threadNumber) {
        if (isFileNameModifyingAllowed(chanName, uri)) {
            if (originalName != null && Preferences.isDownloadOriginalName()) {
                fileName = originalName;
            }
            if (detailName) {
                fileName = getFileNameWithChanBoardThreadData(fileName, chanName, boardName, threadNumber);
            }
        }
        return fileName;
    }

    public void downloadStorage(Context context, Uri uri, String fileName, String originalName,
                                String chanName, String boardName, String threadNumber, String threadTitle) {
        downloadStorage(context, new RequestItem(uri, fileName, originalName),
                chanName, boardName, threadNumber, threadTitle);
    }

    public void downloadStorage(Context context, RequestItem requestItem, String chanName,
                                String boardName, String threadNumber, String threadTitle) {
        ArrayList<RequestItem> requestItems = new ArrayList<>(1);
        requestItems.add(requestItem);
        downloadStorage(context, requestItems, chanName, boardName, threadNumber, threadTitle, false);
    }

    public void downloadStorage(Context context, ArrayList<RequestItem> requestItems, String chanName,
                                String boardName, String threadNumber, String threadTitle, boolean multiple) {
        StorageDialogCallback callback = new StorageDialogCallback(requestItems);
        if (Preferences.isDownloadSubdir(multiple)) {
            boolean modifyingAllowed = false;
            boolean hasOriginalNames = false;
            for (RequestItem requestItem : requestItems) {
                if (isFileNameModifyingAllowed(chanName, requestItem.uri)) {
                    modifyingAllowed = true;
                }
                if (requestItem.originalName != null) {
                    hasOriginalNames = true;
                }
            }
            new DownloadDialog(context, callback, chanName, boardName, threadNumber, threadTitle,
                    modifyingAllowed, modifyingAllowed && hasOriginalNames);
        } else {
            callback.onDirectoryChosen(context, null, Preferences.isDownloadDetailName(),
                    Preferences.isDownloadOriginalName(), chanName, boardName, threadNumber);
        }
    }

    public void saveStreamStorage(Context context, InputStream input, String chanName,
                                  String boardName, String threadNumber, String threadTitle, String fileName, boolean forceNoDialog) {
        StreamDialogCallback callback = new StreamDialogCallback(input, fileName);
        if (Preferences.isDownloadSubdir(false) && !forceNoDialog) {
            new DownloadDialog(context, callback, chanName, boardName, threadNumber, threadTitle, true, false);
        } else {
            callback.onDirectoryChosen(context, null, Preferences.isDownloadDetailName(), false,
                    chanName, boardName, threadNumber);
        }
    }
}
