package com.example.newpdr.utils;


import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.*;

public class FileUtils {
    private static final String TAG = "FileUtils";

    public static boolean writeToFile(File file, String content) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Write file failed: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    public static String readFromFile(File file) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            Log.e(TAG, "Read file failed", e);
            return "";
        }
    }
    // 新增带异常抛出的写入方法
    public static void writeStringToFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
    public static boolean exportToDownloads(Context context, File sourceFile, String mimeType) {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return false;
        }

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File destFile = new File(downloadsDir, sourceFile.getName());

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Export failed", e);
            return false;
        }
    }
}