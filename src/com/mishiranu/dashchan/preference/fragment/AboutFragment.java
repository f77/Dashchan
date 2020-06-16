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

package com.mishiranu.dashchan.preference.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.text.format.DateFormat;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.BackupManager;
import com.mishiranu.dashchan.content.LocaleManager;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.preference.PreferencesActivity;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ToastUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.CommonUtils;

public class AboutFragment extends BasePreferenceFragment {
    private Preference backupDataPreference;
    private Preference changelogPreference;
    private Preference checkForUpdatesPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Preference statisticsPreference = makeButton(null, R.string.preference_statistics, 0, false);
        backupDataPreference = makeButton(null, R.string.preference_backup_data,
                R.string.preference_backup_data_summary, false);
        changelogPreference = makeButton(null, R.string.preference_changelog, 0, false);
        checkForUpdatesPreference = makeButton(null, R.string.preference_check_for_updates, 0, false);
        Preference licensePreference = makeButton(null, R.string.preference_licenses,
                R.string.preference_licenses_summary, false);
        makeButton(null, getString(R.string.preference_version), C.BUILD_VERSION +
                " (" + DateFormat.getDateFormat(getActivity()).format(C.BUILD_TIMESTAMP) + ")", true);

        Intent intent = new Intent(getActivity(), PreferencesActivity.class);
        intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT, StatisticsFragment.class.getName());
        intent.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
        statisticsPreference.setIntent(intent);

        intent = new Intent(getActivity(), PreferencesActivity.class);
        intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT, TextFragment.class.getName());
        intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS,
                TextFragment.createArguments(TextFragment.TYPE_LICENSES, null));
        intent.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
        licensePreference.setIntent(intent);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == backupDataPreference) {
            new BackupDialog().show(getFragmentManager(), BackupDialog.class.getName());
            return true;
        } else if (preference == changelogPreference) {
            new ReadDialog(ReadDialog.TYPE_CHANGELOG).show(getFragmentManager(), ReadDialog.class.getName());
            return true;
        } else if (preference == checkForUpdatesPreference) {
            new ReadDialog(ReadDialog.TYPE_UPDATE).show(getFragmentManager(), ReadDialog.class.getName());
            return true;
        }
        return super.onPreferenceClick(preference);
    }

    public static class BackupDialog extends DialogFragment implements DialogInterface.OnClickListener {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String[] items = getResources().getStringArray(R.array.preference_backup_data_choices);
            return new AlertDialog.Builder(getActivity()).setItems(items, this).create();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == 0) {
                BackupManager.makeBackup(getActivity());
            } else if (which == 1) {
                LinkedHashMap<File, String> filesMap = BackupManager.getAvailableBackups(getActivity());
                if (filesMap != null && filesMap.size() > 0) {
                    new RestoreFragment(filesMap).show(getFragmentManager(), RestoreFragment.class.getName());
                } else {
                    ToastUtils.show(getActivity(), R.string.message_no_backups);
                }
            }
        }
    }

    public static class RestoreFragment extends DialogFragment implements DialogInterface.OnClickListener {
        private static final String EXTRA_FILES = "files";
        private static final String EXTRA_NAMES = "names";

        public RestoreFragment() {
        }

        public RestoreFragment(LinkedHashMap<File, String> filesMap) {
            Bundle args = new Bundle();
            ArrayList<String> files = new ArrayList<>(filesMap.size());
            ArrayList<String> names = new ArrayList<>(filesMap.size());
            for (LinkedHashMap.Entry<File, String> pair : filesMap.entrySet()) {
                files.add(pair.getKey().getAbsolutePath());
                names.add(pair.getValue());
            }
            args.putStringArrayList(EXTRA_FILES, files);
            args.putStringArrayList(EXTRA_NAMES, names);
            setArguments(args);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ArrayList<String> names = getArguments().getStringArrayList(EXTRA_NAMES);
            String[] items = CommonUtils.toArray(names, String.class);
            return new AlertDialog.Builder(getActivity()).setSingleChoiceItems(items, 0, null).setNegativeButton
                    (android.R.string.cancel, null).setPositiveButton(android.R.string.ok, this).create();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            int index = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
            File file = new File(getArguments().getStringArrayList(EXTRA_FILES).get(index));
            BackupManager.loadBackup(getActivity(), file);
        }
    }

    public static class ReadDialog extends DialogFragment implements AsyncManager.Callback {
        private static final String EXTRA_TYPE = "type";

        private static final int TYPE_CHANGELOG = 0;
        private static final int TYPE_UPDATE = 1;

        private static final String TASK_READ_CHANGELOG = "read_changelog";
        private static final String TASK_READ_UPDATE = "read_update";

        public ReadDialog() {
        }

        public ReadDialog(int type) {
            Bundle args = new Bundle();
            args.putInt(EXTRA_TYPE, type);
            setArguments(args);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setMessage(getString(R.string.message_loading));
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            switch (getArguments().getInt(EXTRA_TYPE)) {
                case TYPE_CHANGELOG: {
                    AsyncManager.get(this).startTask(TASK_READ_CHANGELOG, this, null, false);
                    break;
                }
                case TYPE_UPDATE: {
                    AsyncManager.get(this).startTask(TASK_READ_UPDATE, this, null, false);
                    break;
                }
            }
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            switch (getArguments().getInt(EXTRA_TYPE)) {
                case TYPE_CHANGELOG: {
                    AsyncManager.get(this).cancelTask(TASK_READ_CHANGELOG, this);
                    break;
                }
                case TYPE_UPDATE: {
                    AsyncManager.get(this).cancelTask(TASK_READ_UPDATE, this);
                    break;
                }
            }
        }

        private static class ReadUpdateHolder extends AsyncManager.Holder implements ReadUpdateTask.Callback {
            @Override
            public void onReadUpdateComplete(ReadUpdateTask.UpdateDataMap updateDataMap, ErrorItem errorItem) {
                storeResult(updateDataMap, errorItem);
            }
        }

        @Override
        public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
            switch (name) {
                case TASK_READ_CHANGELOG: {
                    ReadChangelogTask task = new ReadChangelogTask(getActivity());
                    task.executeOnExecutor(ReadChangelogTask.THREAD_POOL_EXECUTOR);
                    return task.getHolder();
                }
                case TASK_READ_UPDATE: {
                    ReadUpdateHolder holder = new ReadUpdateHolder();
                    ReadUpdateTask task = new ReadUpdateTask(getActivity(), holder);
                    task.executeOnExecutor(ReadChangelogTask.THREAD_POOL_EXECUTOR);
                    return holder.attach(task);
                }
            }
            return null;
        }

        @Override
        public void onFinishTaskExecution(String name, AsyncManager.Holder holder) {
            dismissAllowingStateLoss();
            switch (name) {
                case TASK_READ_CHANGELOG: {
                    String content = holder.nextArgument();
                    ErrorItem errorItem = holder.nextArgument();
                    if (errorItem == null) {
                        Intent intent = new Intent(getActivity(), PreferencesActivity.class);
                        intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT, TextFragment.class.getName());
                        intent.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS,
                                TextFragment.createArguments(TextFragment.TYPE_CHANGELOG, content, true));
                        intent.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
                        startActivity(intent);
                    } else {
                        ToastUtils.show(getActivity(), errorItem);
                    }
                    break;
                }
                case TASK_READ_UPDATE: {
                    ReadUpdateTask.UpdateDataMap updateDataMap = holder.nextArgument();
                    ErrorItem errorItem = holder.nextArgument();
                    if (updateDataMap != null) {
                        startActivity(UpdateFragment.createUpdateIntent(getActivity(), updateDataMap));
                    } else {
                        ToastUtils.show(getActivity(), errorItem);
                    }
                    break;
                }
            }
        }

        @Override
        public void onRequestTaskCancel(String name, Object task) {
            switch (name) {
                case TASK_READ_CHANGELOG: {
                    ((ReadChangelogTask) task).cancel();
                    break;
                }
                case TASK_READ_UPDATE: {
                    ((ReadUpdateTask) task).cancel();
                    break;
                }
            }
        }
    }

    private static class ReadChangelogTask extends AsyncManager.SimpleTask<Void, Void, Boolean> {
        private final HttpHolder holder = new HttpHolder();

        private final Context context;

        private String result;
        private ErrorItem errorItem;

        public ReadChangelogTask(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public Boolean doInBackground(Void... params) {
            String page = "Changelog-EN";
            for (Locale locale : LocaleManager.getInstance().list(context)) {
                String language = locale.getLanguage();
                if ("ru".equals(language)) {
                    page = "Changelog-RU";
                    break;
                }
            }
            Uri uri = ChanLocator.getDefault().buildPathWithHost("raw.githubusercontent.com", "f77", "Dashchan", "master", "CHANGELOG.md");
            try {
                String result = new HttpRequest(uri, holder).read().getString();
                if (result == null) {
                    errorItem = new ErrorItem(ErrorItem.TYPE_UNKNOWN);
                    return false;
                } else {
                    this.result = result;
                    return true;
                }
            } catch (HttpException e) {
                errorItem = e.getErrorItemAndHandle();
                holder.disconnect();
                return false;
            } finally {
                holder.cleanup();
            }
        }

        @Override
        protected void onStoreResult(AsyncManager.Holder holder, Boolean result) {
            holder.storeResult(this.result, errorItem);
        }

        @Override
        public void cancel() {
            cancel(true);
            holder.interrupt();
        }
    }

    private static class ChangelogGroupCallback implements GroupParser.Callback {
        private String result;

        public static String parse(String source) {
            ChangelogGroupCallback callback = new ChangelogGroupCallback();
            try {
                GroupParser.parse(source, callback);
            } catch (ParseException e) {
                Log.persistent().stack(e);
            }
            return callback.result;
        }

        @Override
        public boolean onStartElement(GroupParser parser, String tagName, String attrs) {
            return "div".equals(tagName) && "markdown-body".equals(parser.getAttr(attrs, "class"));
        }

        @Override
        public void onEndElement(GroupParser parser, String tagName) {
        }

        @Override
        public void onText(GroupParser parser, String source, int start, int end) throws ParseException {
        }

        @Override
        public void onGroupComplete(GroupParser parser, String text) throws ParseException {
            result = text;
            throw new ParseException();
        }
    }
}
