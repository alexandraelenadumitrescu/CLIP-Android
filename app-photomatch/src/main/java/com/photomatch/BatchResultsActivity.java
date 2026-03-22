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
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.gson.Gson;
import com.photomatch.api.BatchResult;
import com.photomatch.api.ProcessResponse;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class BatchResultsActivity extends AppCompatActivity {

    private BatchActivity.BatchCache cache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_results);

        String cachePath = getIntent().getStringExtra(BatchActivity.EXTRA_CACHE_PATH);
        if (cachePath == null) { finish(); return; }

        try (FileReader fr = new FileReader(cachePath)) {
            cache = new Gson().fromJson(fr, BatchActivity.BatchCache.class);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load results", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (cache == null || cache.response == null) { finish(); return; }

        RecyclerView rv = findViewById(R.id.rvResults);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.setAdapter(new ResultsAdapter());

        Button btnExport = findViewById(R.id.btnExport);
        btnExport.setOnClickListener(v -> exportAll());
    }

    private class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.VH> {

        private final List<BatchResult> results = cache.response.results;
        private final List<String>      uriStrings = cache.originalUris;

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater()
                .inflate(R.layout.item_batch_result, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            BatchResult result = results.get(position);

            // Original thumbnail — load from URI
            if (uriStrings != null && position < uriStrings.size()) {
                Uri uri = Uri.parse(uriStrings.get(position));
                Glide.with(BatchResultsActivity.this)
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .into(holder.ivOriginal);
            }

            // Corrected thumbnail — decode base64
            byte[] correctedBytes = decodeBase64(result.correctedB64);
            if (correctedBytes != null) {
                Bitmap bmp = decodeSampled(correctedBytes, 256);
                holder.ivCorrected.setImageBitmap(bmp);
            }

            holder.tvSimilarity.setText(
                String.format(Locale.US, "%d%%", Math.round(result.similarity * 100)));
            holder.tvRetrieved.setText(result.retrieved);

            holder.itemView.setOnClickListener(v -> openDetail(position, result));
        }

        @Override public int getItemCount() { return results.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivOriginal;
            final ImageView ivCorrected;
            final TextView  tvSimilarity;
            final TextView  tvRetrieved;
            VH(android.view.View v) {
                super(v);
                ivOriginal   = v.findViewById(R.id.ivOriginal);
                ivCorrected  = v.findViewById(R.id.ivCorrected);
                tvSimilarity = v.findViewById(R.id.tvSimilarity);
                tvRetrieved  = v.findViewById(R.id.tvRetrieved);
            }
        }
    }

    private void openDetail(int position, BatchResult result) {
        // Build a minimal ProcessResponse for ResponseCache so DetailActivity can compare
        ProcessResponse pr = new ProcessResponse();
        pr.finalB64    = result.correctedB64;
        pr.retrieved   = result.retrieved;
        pr.similarity  = result.similarity;

        // Encode original image from URI into base64 for the "before" side
        if (cache.originalUris != null && position < cache.originalUris.size()) {
            pr.originalB64 = loadUriAsBase64(Uri.parse(cache.originalUris.get(position)));
        }

        ResponseCache.current = pr;
        startActivity(new Intent(this, DetailActivity.class));
    }

    private String loadUriAsBase64(Uri uri) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
            if (bmp == null) return null;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            bmp.recycle();
            return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        } catch (IOException e) {
            return null;
        }
    }

    private void exportAll() {
        if (cache.response.results.isEmpty()) {
            Toast.makeText(this, "No results to export", Toast.LENGTH_SHORT).show();
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            int saved = 0;
            for (BatchResult result : cache.response.results) {
                byte[] bytes = decodeBase64(result.correctedB64);
                if (bytes != null) {
                    try {
                        saveToGallery(bytes, result.retrieved);
                        saved++;
                    } catch (IOException ignored) {}
                }
            }
            final int finalSaved = saved;
            runOnUiThread(() ->
                Toast.makeText(this, "Saved " + finalSaved + " images to gallery",
                    Toast.LENGTH_SHORT).show());
        });
    }

    private void saveToGallery(byte[] imageBytes, String baseName) throws IOException {
        String filename = "photomatch_batch_" + baseName + "_" + System.currentTimeMillis() + ".jpg";
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
            cv.put(MediaStore.Images.Media.DATA, new File(dir, filename).getAbsolutePath());
        }

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new IOException("MediaStore insert returned null");
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            os.write(imageBytes);
        }
    }

    private static byte[] decodeBase64(String b64) {
        if (b64 == null) return null;
        return Base64.decode(b64, Base64.DEFAULT);
    }

    private static Bitmap decodeSampled(byte[] bytes, int maxSide) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        int inSampleSize = 1;
        while (Math.max(opts.outWidth, opts.outHeight) / (inSampleSize * 2) > maxSide) {
            inSampleSize *= 2;
        }
        opts.inSampleSize = inSampleSize;
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
    }
}
