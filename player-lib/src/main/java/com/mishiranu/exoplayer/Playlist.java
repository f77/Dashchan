package com.mishiranu.exoplayer;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * Simple playlist class for the player.
 */
public class Playlist implements Parcelable {
    protected ArrayList<Uri> URIs;
    protected int currentPosition;

    public Playlist(ArrayList<Uri> URIs, int currentPosition) {
        this.URIs = URIs;
        this.currentPosition = currentPosition;
    }

    public ArrayList<Uri> getURIs() {
        return URIs;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(URIs);
        dest.writeInt(currentPosition);
    }

    // this is used to regenerate your object. All Parcelables must have a CREATOR that implements these two methods
    public static final Parcelable.Creator<Playlist> CREATOR = new Parcelable.Creator<Playlist>() {
        @SuppressWarnings("unchecked")
        public Playlist createFromParcel(Parcel in) {
            return new Playlist(in.readArrayList(getClass().getClassLoader()), in.readInt());
        }

        public Playlist[] newArray(int size) {
            return new Playlist[size];
        }
    };
}
