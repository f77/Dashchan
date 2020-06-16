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

package com.mishiranu.dashchan.ui.posting.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Pair;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.ui.posting.AttachmentHolder;

import java.util.List;

public class AttachmentRatingDialog extends PostingDialog implements DialogInterface.OnClickListener {
    public static final String TAG = AttachmentRatingDialog.class.getName();

    private static final String EXTRA_ATTACHMENT_INDEX = "attachmentIndex";

    public AttachmentRatingDialog() {
    }

    public AttachmentRatingDialog(int attachmentIndex) {
        Bundle args = new Bundle();
        args.putInt(EXTRA_ATTACHMENT_INDEX, attachmentIndex);
        setArguments(args);
    }

    private AttachmentHolder holder;
    private String[] ratings;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity activity = getActivity();
        List<Pair<String, String>> attachmentRatingItems = getAttachmentRatingItems();
        holder = getAttachmentHolder(EXTRA_ATTACHMENT_INDEX);
        if (holder == null || attachmentRatingItems == null) {
            dismiss();
            return new Dialog(activity);
        }
        String[] items = new String[attachmentRatingItems.size()];
        ratings = new String[attachmentRatingItems.size()];
        int checkedItem = 0;
        for (int i = 0; i < items.length; i++) {
            Pair<String, String> ratingItem = attachmentRatingItems.get(i);
            items[i] = ratingItem.second;
            ratings[i] = ratingItem.first;
            if (ratingItem.first.equals(holder.rating)) {
                checkedItem = i;
            }
        }
        return new AlertDialog.Builder(activity).setTitle(R.string.text_rating).setSingleChoiceItems(items,
                checkedItem, this).setNegativeButton(android.R.string.cancel, null).create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        dismiss();
        holder.rating = ratings[which];
    }
}
