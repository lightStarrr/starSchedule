package com.suda.yzune.wakeupschedule;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public final class WakeUpProxyProvider extends ContentProvider {
    private static final String TAG = "WakeUpProxyProvider";
    private static final String SOURCE_AUTHORITY = "com.star.schedule.export";
    private static final Uri REFRESH_URI =
            Uri.parse("content://com.suda.yzune.wakeupschedule.provider/refresh");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        String path = uri.getPathSegments().isEmpty() ? "" : uri.getPathSegments().get(0);
        if ("refresh".equals(path)) {
            return null;
        }
        if (!isSupported(path)) {
            return null;
        }

        try (Cursor source = getContext().getContentResolver().query(
                uri.buildUpon().authority(SOURCE_AUTHORITY).build(),
                null, null, null, null)) {
            if (source != null && source.moveToFirst()) {
                int codeColumn = source.getColumnIndex("code");
                int dataColumn = source.getColumnIndex("data");
                if (codeColumn >= 0 && dataColumn >= 0) {
                    String data = source.getString(dataColumn);
                    if (data != null) {
                        return oneRow(source.getInt(codeColumn), data);
                    }
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "星课程表数据源不可用", error);
        }
        return fallback(path);
    }

    private static boolean isSupported(String path) {
        return "has_init".equals(path)
                || "show_table_id".equals(path)
                || "table_list".equals(path)
                || "course_list".equals(path)
                || "next_course_list".equals(path);
    }

    private static Cursor fallback(String path) {
        switch (path) {
            case "has_init":
                return oneRow(0, "{\"has_init\":true}");
            case "show_table_id":
                return oneRow(0, "{\"table_id\":1}");
            case "table_list":
                return oneRow(0, "[{\"id\":1,\"tableName\":\"星课程表\"}]");
            case "course_list":
            case "next_course_list":
                return oneRow(0, "[]");
            default:
                return null;
        }
    }

    private static Cursor oneRow(int code, String data) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"code", "data"});
        cursor.addRow(new Object[]{code, data});
        return cursor;
    }

    public static void notifySystem(Context context) {
        ContentResolver resolver = context.getContentResolver();
        resolver.notifyChange(REFRESH_URI, null);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> resolver.notifyChange(REFRESH_URI, null), 1_000L);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only provider");
    }
}
