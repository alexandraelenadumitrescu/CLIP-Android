package com.photomatch;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.photomatch.api.ApiClient;
import com.photomatch.api.ProcessResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

public class ProcessingActivity extends AppCompatActivity {

    private static final String[] LOG_LINES = {
        "extracting visual semantics_",
        "measuring technical defects_",
        "searching 3499 reference photographs_",
        "applying expert colour grade_",
    };

    private String  imagePath;
    private boolean useStyle;
    private String  sessionId;
    private ExecutorService executor;

    private TextView tvLog;
    private TextView tvError;
    private Button btnRetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        imagePath = getIntent().getStringExtra(MainActivity.EXTRA_IMAGE_PATH);
        if (imagePath == null) {
            finish();
            return;
        }
        useStyle  = getIntent().getBooleanExtra("use_style", false);
        sessionId = getIntent().getStringExtra("session_id");

        tvLog    = findViewById(R.id.tvLog);
        tvError  = findViewById(R.id.tvError);
        btnRetry = findViewById(R.id.btnRetry);

        // Show selected image
        ImageView ivPreview = findViewById(R.id.ivPreview);
        Glide.with(this)
            .load(new File(imagePath))
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .fitCenter()
            .into(ivPreview);

        btnRetry.setOnClickListener(v -> {
            // Restart this activity with the same image
            Intent intent = new Intent(this, ProcessingActivity.class);
            intent.putExtra(MainActivity.EXTRA_IMAGE_PATH, imagePath);
            startActivity(intent);
            finish();
        });

        startPipeline();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
    }

    private void startPipeline() {
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                appendLog(LOG_LINES[0]); // extracting visual semantics_
                sleep(500);
                appendLog(LOG_LINES[1]); // measuring technical defects_
                sleep(500);
                appendLog(LOG_LINES[2]); // searching 3499 reference photographs_

                // Upload image to server — this blocks until server responds
                ProcessResponse response = (useStyle && sessionId != null)
                    ? callStyleServer(imagePath, sessionId)
                    : callServer(imagePath);

                appendLog(LOG_LINES[3]); // applying expert colour grade_
                sleep(300);

                // Success
                ResponseCache.current = response;
                runOnUiThread(() -> {
                    startActivity(new Intent(this, ResultsActivity.class));
                    finish();
                });

            } catch (Exception e) {
                runOnUiThread(() -> showError("Server error: " + e.getMessage()));
            }
        });
    }

    private ProcessResponse callServer(String filePath) throws IOException {
        File imageFile = new File(filePath);
        RequestBody reqBody = RequestBody.create(MediaType.parse("image/jpeg"), imageFile);
        MultipartBody.Part part = MultipartBody.Part.createFormData(
            "file", imageFile.getName(), reqBody);

        Response<ProcessResponse> response = ApiClient.getInstance()
            .getService().process(part).execute();

        if (!response.isSuccessful()) {
            throw new IOException("HTTP " + response.code()
                + (response.errorBody() != null ? ": " + response.errorBody().string() : ""));
        }
        ProcessResponse body = response.body();
        if (body == null) throw new IOException("Empty response from server");
        return body;
    }

    private ProcessResponse callStyleServer(String filePath, String sid) throws IOException {
        File imageFile = new File(filePath);
        RequestBody reqBody = RequestBody.create(MediaType.parse("image/jpeg"), imageFile);
        MultipartBody.Part part = MultipartBody.Part.createFormData(
            "file", imageFile.getName(), reqBody);
        RequestBody sidBody = RequestBody.create(MediaType.parse("text/plain"), sid);

        Response<ProcessResponse> response = ApiClient.getInstance()
            .getService().styleProcess(part, sidBody).execute();

        if (!response.isSuccessful()) {
            throw new IOException("HTTP " + response.code()
                + (response.errorBody() != null ? ": " + response.errorBody().string() : ""));
        }
        ProcessResponse body = response.body();
        if (body == null) throw new IOException("Empty response from server");
        return body;
    }

    private void appendLog(String line) {
        runOnUiThread(() -> tvLog.append(line + "\n"));
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.VISIBLE);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
