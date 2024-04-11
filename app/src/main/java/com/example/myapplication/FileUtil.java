package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

public class FileUtil {
    /* **********************************************************************
     * Variable
     ********************************************************************** */
    private static final String TAG = "FileUtils";
    private static final String FALLBACK_COPY_FOLDER = "upload_part";
    private final Context context;
    private final Activity activity;
    private final int FACE_REGISTER_REQ_PERMISSION_BELOW_11_CODE = 301;
    private final int FACE_REGISTER_REQ_PERMISSION_CODE = 300;

    /* **********************************************************************
     * Constructor
     ********************************************************************** */
    public FileUtil(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    /* **********************************************************************
     * Function
     ********************************************************************** */
    public String getPath(Uri uri) {
        String selection;
        String[] selectionArgs;

        if (isExternalStorageDocument(uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            String fullPath = getPathFromExtSD(split);
            if (fullPath == null || !fileExists(fullPath)) {
                Log.d(TAG, "Copy files as a fallback");
                fullPath = copyFileToInternalStorage(uri, FALLBACK_COPY_FOLDER);
            }
            return TextUtils.isEmpty(fullPath) ? null : fullPath;
        }

        if (isDownloadsDocument(uri)) {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String fileName = cursor.getString(0);
                String path = Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName;
                if (!TextUtils.isEmpty(path)) {
                    return path;
                }
            }
            if (cursor != null) {
                cursor.close();
            }

            String id = DocumentsContract.getDocumentId(uri);
            if (!TextUtils.isEmpty(id)) {
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "");
                }

                String[] contentUriPrefixesToTry = {"content://downloads/public_downloads", "content://downloads/my_downloads"};
                for (String contentUriPrefix : contentUriPrefixesToTry) {
                    try {
                        Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.parseLong(id));
                        return getDataColumn(context, contentUri, null, null);
                    } catch (NumberFormatException e) {
                        // In Android 8 and Android P the id is not a number
                        return Objects.requireNonNull(uri.getPath()).replaceFirst("^/document/raw:", "").replaceFirst("^raw:", "");
                    }
                }
            }
        }

        if (isMediaDocument(uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            String type = split[0];
            Log.d(TAG, "MEDIA DOCUMENT TYPE: " + type);

            Uri contentUri = null;
            switch (type) {
                case "image":
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    break;
                case "video":
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    break;
                case "audio":
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    break;
                case "document":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentUri = MediaStore.Files.getContentUri(MediaStore.getVolumeName(uri));
                    } else {
                        File file = new File(uri.getPath());
                        String authority = context.getPackageName() + ".provider";
                        contentUri = FileProvider.getUriForFile(context.getApplicationContext(), authority, file);
                    }
                    break;
            }

            selection = "_id=?";
            selectionArgs = new String[]{split[1]};
            return getDataColumn(context, contentUri, selection, selectionArgs);
        }

        if (isGoogleDriveUri(uri)) {
            return getDriveFilePath(uri);
        }

        if (isWhatsAppFile(uri)) {
            return getFilePathForWhatsApp(uri);
        }

        if ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme())) {
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }

            if (isGoogleDriveUri(uri)) {
                return getDriveFilePath(uri);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyFileToInternalStorage(uri, FALLBACK_COPY_FOLDER);
            } else {
                return getDataColumn(context, uri, null, null);
            }
        }

        return copyFileToInternalStorage(uri, FALLBACK_COPY_FOLDER);
    }

    public String getDriveFilePath(Uri uri) {
        Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
        int nameIndex = 0;
        if (returnCursor != null) {
            nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        }
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();

        File file = new File(context.getCacheDir(), name);
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(file);
            int read;
            int maxBufferSize = 1024 * 1024;
            int bytesAvailable = inputStream.available();

            int bufferSize = Math.min(bytesAvailable, maxBufferSize);
            byte[] buffers = new byte[bufferSize];
            while ((read = inputStream.read(buffers)) != -1) {
                outputStream.write(buffers, 0, read);
            }
            Log.e(TAG, "Size " + file.length());
            inputStream.close();
            outputStream.close();
            Log.e(TAG, "Path " + file.getPath());
            Log.e(TAG, "Size " + file.length());
        } catch (Exception e) {
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
        return file.getPath();
    }

    public String copyFileToInternalStorage(Uri uri, String newDirName) {
        Cursor returnCursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null
        );
        int nameIndex = 0;
        if (returnCursor != null) {
            nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        }
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();

        File output;
        if (!TextUtils.isEmpty(newDirName)) {
            String randomCollisionAvoidance = UUID.randomUUID().toString();
            File dir = new File(context.getExternalFilesDir(null) + File.separator + newDirName + File.separator + randomCollisionAvoidance);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Log.d(TAG, "copyFileToInternalStorage1: " + context.getExternalFilesDir(null));
            output = new File(context.getExternalFilesDir(null) + File.separator + newDirName + File.separator + randomCollisionAvoidance + File.separator + name);
        } else {
            output = new File(context.getExternalFilesDir(null) + File.separator + name);
        }

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(output);
            int read;
            byte[] buffers = new byte[1024];
            if (inputStream != null) {
                while ((read = inputStream.read(buffers)) != -1) {
                    outputStream.write(buffers, 0, read);
                }
            }
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }

        return output.getPath();
    }

    public String getFilePathForWhatsApp(Uri uri) {
        return copyFileToInternalStorage(uri, "whatsapp");
    }

    public String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String column = "_data";
        String[] projection = {column};
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, FACE_REGISTER_REQ_PERMISSION_BELOW_11_CODE);
            } else {
                try {
                    cursor = context.getContentResolver().query(
                            uri, projection, selection, selectionArgs, null
                    );
                    Log.d(TAG, "getDataColumn: " + cursor.getColumnCount());
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndexOrThrow(column);
                        return cursor.getString(index);
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        } else {
            if (Environment.isExternalStorageManager()) {
                try {
                    cursor = context.getContentResolver().query(
                            uri, projection, selection, selectionArgs, null
                    );
                    Log.d(TAG, "getDataColumn: " + cursor.getColumnCount());
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndexOrThrow(column);
                        return cursor.getString(index);
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            } else {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", activity.getPackageName())));
                    activity.startActivityForResult(intent, FACE_REGISTER_REQ_PERMISSION_CODE);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.startActivityForResult(intent, FACE_REGISTER_REQ_PERMISSION_CODE);
                }

            }
        }
        return null;
    }

    public boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    public boolean isWhatsAppFile(Uri uri) {
        return "com.whatsapp.provider.media".equals(uri.getAuthority());
    }

    public boolean isGoogleDriveUri(Uri uri) {
        return "com.google.android.apps.docs.storage".equals(uri.getAuthority()) ||
                "com.google.android.apps.docs.storage.legacy".equals(uri.getAuthority());
    }

    private static boolean fileExists(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    private static String getPathFromExtSD(String[] pathData) {
        String type = pathData[0];
        String relativePath = File.separator + pathData[1];
        String fullPath;

        Log.d(TAG, "MEDIA EXTSD TYPE: " + type);
        Log.d(TAG, "Relative path: " + relativePath);

        if ("primary".equalsIgnoreCase(type)) {
            fullPath = Environment.getExternalStorageDirectory().toString() + relativePath;
            if (fileExists(fullPath)) {
                return fullPath;
            }
        }
        if ("home".equalsIgnoreCase(type)) {
            fullPath = "/storage/emulated/0/Documents" + relativePath;
            if (fileExists(fullPath)) {
                return fullPath;
            }
        }

        fullPath = System.getenv("SECONDARY_STORAGE") + relativePath;
        if (fileExists(fullPath)) {
            return fullPath;
        }

        fullPath = System.getenv("EXTERNAL_STORAGE") + relativePath;
        return fileExists(fullPath) ? fullPath : null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

}
