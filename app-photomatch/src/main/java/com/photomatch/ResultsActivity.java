package com.photomatch;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.photomatch.api.ApiClient;
import com.photomatch.api.ProcessResponse;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;

public class ResultsActivity extends AppCompatActivity {

    private static final String FILL  = "████████";
    private static final String EMPTY = "░░░░░░░░";

    private static final String[] DEFECT_KEYS   = {"blur", "noise", "overexposure", "underexposure", "compression"};
    private static final String[] DEFECT_LABELS = {"BLUR ", "NOISE", "OVER ", "UNDER", "COMP "};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        ProcessResponse resp = ResponseCache.current;
        if (resp == null) { finish(); return; }

        ImageView ivResult   = findViewById(R.id.ivResult);
        TextView  tvDefects  = findViewById(R.id.tvDefects);
        Button    btnMatch   = findViewById(R.id.btnViewMatch);
        Button    btnSave    = findViewById(R.id.btnSave);

        // Show corrected image
        final byte[] finalBytes = decodeBase64(resp.finalB64);
        if (finalBytes != null) {
            ivResult.setImageBitmap(decodeSampled(finalBytes, 1024));
        }

        // Defect bars
        tvDefects.setText(buildDefectBars(resp.defects));

        btnMatch.setOnClickListener(v ->
            startActivity(new Intent(this, DetailActivity.class)));

        btnSave.setOnClickListener(v -> saveToGallery(finalBytes));
    }

    private String buildDefectBars(Map<String, Float> defects) {
        if (defects == null) return "No defect data";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DEFECT_KEYS.length; i++) {
            float val = defects.containsKey(DEFECT_KEYS[i]) ? defects.get(DEFECT_KEYS[i]) : 0f;
            int filled = Math.min(8, Math.max(0, Math.round(val * 8)));
            String bar = FILL.substring(0, filled) + EMPTY.substring(0, 8 - filled);
            sb.append(String.format(Locale.US, "%s %s %.2f\n", DEFECT_LABELS[i], bar, val));
        }
        // Trim trailing newline
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private void saveToGallery(byte[] imageBytes) {
        if (imageBytes == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String filename = "photomatch_" + System.currentTimeMillis() + ".jpg";
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoMatch");
            } else {
                File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "PhotoMatch");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                cv.put(MediaStore.Images.Media.DATA,
                    new File(dir, filename).getAbsolutePath());
            }

            Uri uri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IOException("MediaStore insert returned null");

            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(imageBytes);
            }
            Toast.makeText(this, "Saved to gallery", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static Bitmap decodeSampled(byte[] bytes, int maxSide) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        opts.inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight, maxSide);
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
    }

    private static int computeSampleSize(int width, int height, int maxSide) {
        int inSampleSize = 1;
        while (Math.max(width, height) / (inSampleSize * 2) > maxSide) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private static byte[] decodeBase64(String b64) {
        if (b64 == null) return null;
        return android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
    }
}
