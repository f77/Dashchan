package com.mishiranu.exoplayer;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple playlist class for the player.
 */
public class Playlist implements Parcelable {
    protected LinkedHashMap<Uri, Parcelable> medias;
    protected int currentPosition;

    public Playlist(LinkedHashMap<Uri, Parcelable> medias, int currentPosition) {
        this.medias = medias;
        this.currentPosition = currentPosition;
    }

    public LinkedHashMap<Uri, Parcelable> getMedias() {
        return medias;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public Uri getUriByPosition(int position) {
        return ((Uri) medias.keySet().toArray()[position]);
    }

    public Parcelable getExtraByPosition(int position) {
        return medias.get(getUriByPosition(position));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Make 2 ArrayLists to parcel the data.
        ArrayList<Uri> listUris = new ArrayList<>();
        ArrayList<Parcelable> listParcelables = new ArrayList<>();
        for (Map.Entry<Uri, Parcelable> entry : medias.entrySet()) {
            listUris.add(entry.getKey());
            listParcelables.add(entry.getValue());
        }

        dest.writeList(listUris);
        dest.writeList(listParcelables);
        dest.writeInt(currentPosition);
    }

    // this is used to regenerate your object. All Parcelables must have a CREATOR that implements these two methods
    public static final Parcelable.Creator<Playlist> CREATOR = new Parcelable.Creator<Playlist>() {
        @SuppressWarnings("unchecked")
        public Playlist createFromParcel(Parcel in) {
            // Restore LinkedHashMap from 2 ArrayLists.
            ArrayList<Uri> listUris = in.readArrayList(getClass().getClassLoader());
            ArrayList<Parcelable> listParcelables = in.readArrayList(getClass().getClassLoader());
            LinkedHashMap<Uri, Parcelable> medias = new LinkedHashMap<>();
            assert listUris != null;
            assert listParcelables != null;
            for (int i = 0; i < listUris.size(); i++) {
                medias.put(listUris.get(i), listParcelables.get(i));
            }

            return new Playlist(medias, in.readInt());
        }

        public Playlist[] newArray(int size) {
            return new Playlist[size];
        }
    };
}
