package com.crdroid.updater;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

public class MirrorsDbHelper extends SQLiteOpenHelper {

    private static MirrorsDbHelper mirrorsDbHelper = null;

    public static final int DATABASE_VERSION = 1;
    public static final String MIRRORS_DATABASE_NAME = "mirrors.db";

    public static class MirrorsEntry implements BaseColumns {
        public static final String TABLE_NAME = "mirrors";
        public static final String COLUMN_NAME_DOWNLOAD_ID = "download_id";
        public static final String COLUMN_NAME_MIRROR = "mirror_name";
        public static final String COLUMN_NAME_MIRROR_URL = "mirror_url";
    }

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + MirrorsDbHelper.MirrorsEntry.TABLE_NAME + " (" +
                    MirrorsEntry._ID + " INTEGER PRIMARY KEY," +
                    MirrorsEntry.COLUMN_NAME_DOWNLOAD_ID + " TEXT NOT NULL UNIQUE," +
                    MirrorsEntry.COLUMN_NAME_MIRROR + " TEXT," +
                    MirrorsEntry.COLUMN_NAME_MIRROR_URL + " TEXT)";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + MirrorsDbHelper.MirrorsEntry.TABLE_NAME;

    public static MirrorsDbHelper getInstance(Context context) {
        if (mirrorsDbHelper == null) {
            mirrorsDbHelper = new MirrorsDbHelper(context.getApplicationContext());
        }
        return mirrorsDbHelper;
    }

    private MirrorsDbHelper(Context context) {
        super(context, MIRRORS_DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public void setUpdate(String downloadId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MirrorsEntry.COLUMN_NAME_DOWNLOAD_ID, downloadId);
        db.insert(MirrorsEntry.TABLE_NAME, null, values);
    }

    public Boolean isUpdateExists(String downloadId) {
        SQLiteDatabase db = getWritableDatabase();
        boolean isExists = false;
        String selection = MirrorsEntry.COLUMN_NAME_DOWNLOAD_ID + " = ?";
        String[] selectionArgs = {downloadId};
        try (Cursor cursor = db.query(MirrorsEntry.TABLE_NAME, null, selection, selectionArgs, null, null, null)) {
            while (cursor.moveToNext()) {
                int index = cursor.getColumnIndex(MirrorsEntry.COLUMN_NAME_DOWNLOAD_ID);
                String res = cursor.getString(index);
                if (res.equals(downloadId)) {
                    isExists = true;
                    break;
                }
            }
        }
        return isExists;
    }

    public void delUpdate(String downloadId) {
        SQLiteDatabase db = getWritableDatabase();
        String selection = MirrorsEntry.COLUMN_NAME_DOWNLOAD_ID + " = ?";
        String[] selectionArgs = {downloadId};
        db.delete(MirrorsEntry.TABLE_NAME, selection, selectionArgs);
    }

    public void setMirrorUrl(String mirrorUrl, String downloadId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        String download_id_column = MirrorsEntry.COLUMN_NAME_DOWNLOAD_ID + " = ?";
        String[] args = {downloadId};
        values.put(MirrorsEntry.COLUMN_NAME_MIRROR_URL, mirrorUrl);
        db.update(MirrorsEntry.TABLE_NAME, values, download_id_column, args);
    }

    public String getMirrorUrl(String downloadId) {
        SQLiteDatabase db = getWritableDatabase();
        String res = "";
        String download_id_column = MirrorsEntry.COLUMN_NAME_DOWNLOAD_ID + " = ?";
        String[] get_columns = {MirrorsEntry.COLUMN_NAME_MIRROR_URL};
        String[] args = {downloadId};
        try (Cursor cursor = db.query(MirrorsEntry.TABLE_NAME, get_columns, download_id_column, args, null, null, null)) {
            while (cursor.moveToNext()) {
                int index = cursor.getColumnIndex(MirrorsEntry.COLUMN_NAME_MIRROR_URL);
                res = cursor.getString(index);
            }
        }
        return res;
    }

    public void setMirrorName(String mirrorName, String downloadId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        String download_id_column = MirrorsEntry.COLUMN_NAME_DOWNLOAD_ID + " = ?" ;
        String[] args = {downloadId};
        values.put(MirrorsEntry.COLUMN_NAME_MIRROR, mirrorName);
        db.update(MirrorsEntry.TABLE_NAME, values, download_id_column, args);
    }

    public String getMirrorName(String downloadId) {
        SQLiteDatabase db = getWritableDatabase();
        String res = "unknown";
        String download_id_column = MirrorsEntry.COLUMN_NAME_DOWNLOAD_ID + " = ?";
        String[] get_columns = {MirrorsEntry.COLUMN_NAME_MIRROR};
        String[] args = {downloadId};
        try (Cursor cursor = db.query(MirrorsEntry.TABLE_NAME, get_columns, download_id_column, args, null, null, null)) {
            while (cursor.moveToNext()) {
                int index = cursor.getColumnIndex(MirrorsEntry.COLUMN_NAME_MIRROR);
                res = cursor.getString(index);
            }
        }
        return res;
    }
}
