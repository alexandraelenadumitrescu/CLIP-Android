package com.photomatch;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BurstResultsActivity extends AppCompatActivity {

    private BurstActivity.BurstCache cache;
    private final Set<Integer> expanded = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_burst_results);

        String cachePath = getIntent().getStringExtra(BurstActivity.EXTRA_CACHE_PATH);
        if (cachePath == null) { finish(); return; }

        try (FileReader fr = new FileReader(cachePath)) {
            cache = new Gson().fromJson(fr, BurstActivity.BurstCache.class);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load results", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (cache == null || cache.clusters == null) { finish(); return; }

        RecyclerView rv = findViewById(R.id.rvClusters);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new ClusterAdapter());
    }

    // --- Cluster list adapter ---

    private class ClusterAdapter extends RecyclerView.Adapter<ClusterAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater()
                .inflate(R.layout.item_burst_cluster, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            List<Integer> cluster = cache.clusters.get(position);
            int clusterSize = cluster.size();

            // Sort cluster indices by score descending
            List<Integer> sorted = new ArrayList<>(cluster);
            sorted.sort((a, b) -> Float.compare(
                cache.scores.get(b), cache.scores.get(a)));

            int bestIdx = sorted.get(0);
            Uri bestUri = Uri.parse(cache.uriStrings.get(bestIdx));

            // Front photo
            Glide.with(BurstResultsActivity.this)
                .load(bestUri)
                .placeholder(R.color.color_surface)
                .centerCrop()
                .into(holder.ivFront);

            // Back photos for stack effect
            if (clusterSize >= 2) {
                Uri back1Uri = Uri.parse(cache.uriStrings.get(sorted.get(1)));
                Glide.with(BurstResultsActivity.this)
                    .load(back1Uri)
                    .placeholder(R.color.color_surface)
                    .centerCrop()
                    .into(holder.ivBack1);
                holder.ivBack1.setVisibility(View.VISIBLE);
            } else {
                holder.ivBack1.setVisibility(View.GONE);
            }

            if (clusterSize >= 3) {
                Uri back2Uri = Uri.parse(cache.uriStrings.get(sorted.get(2)));
                Glide.with(BurstResultsActivity.this)
                    .load(back2Uri)
                    .placeholder(R.color.color_surface)
                    .centerCrop()
                    .into(holder.ivBack2);
                holder.ivBack2.setVisibility(View.VISIBLE);
            } else {
                holder.ivBack2.setVisibility(View.GONE);
            }

            // Score badge
            float bestScore = cache.scores.get(bestIdx);
            holder.tvScore.setText(String.format(Locale.US, "★ %.2f", bestScore));

            // Cluster count label (hidden for singletons)
            if (clusterSize > 1) {
                holder.tvClusterCount.setText("1 of " + clusterSize);
                holder.tvClusterCount.setVisibility(View.VISIBLE);
            } else {
                holder.tvClusterCount.setVisibility(View.GONE);
            }

            // Expand/collapse on tap
            boolean isExpanded = expanded.contains(position);
            if (isExpanded) {
                holder.rvPhotos.setVisibility(View.VISIBLE);
                holder.rvPhotos.setLayoutManager(
                    new LinearLayoutManager(BurstResultsActivity.this,
                        LinearLayoutManager.HORIZONTAL, false));
                holder.rvPhotos.setAdapter(new PhotoStripAdapter(sorted));
            } else {
                holder.rvPhotos.setVisibility(View.GONE);
            }

            // Only tappable if cluster has more than 1 photo
            if (clusterSize > 1) {
                holder.itemView.setOnClickListener(v -> {
                    int pos = holder.getAdapterPosition();
                    if (expanded.contains(pos)) expanded.remove(pos);
                    else                        expanded.add(pos);
                    notifyItemChanged(pos);
                });
            } else {
                holder.itemView.setOnClickListener(null);
            }
        }

        @Override
        public void onViewRecycled(VH holder) {
            super.onViewRecycled(holder);
            Glide.with(BurstResultsActivity.this).clear(holder.ivFront);
            Glide.with(BurstResultsActivity.this).clear(holder.ivBack1);
            Glide.with(BurstResultsActivity.this).clear(holder.ivBack2);
        }

        @Override public int getItemCount() { return cache.clusters.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView  ivBack2;
            final ImageView  ivBack1;
            final ImageView  ivFront;
            final TextView   tvScore;
            final TextView   tvClusterCount;
            final RecyclerView rvPhotos;
            VH(android.view.View v) {
                super(v);
                ivBack2        = v.findViewById(R.id.ivBack2);
                ivBack1        = v.findViewById(R.id.ivBack1);
                ivFront        = v.findViewById(R.id.ivFront);
                tvScore        = v.findViewById(R.id.tvScore);
                tvClusterCount = v.findViewById(R.id.tvClusterCount);
                rvPhotos       = v.findViewById(R.id.rvPhotos);
            }
        }
    }

    // --- Expanded horizontal photo strip adapter ---

    private class PhotoStripAdapter extends RecyclerView.Adapter<PhotoStripAdapter.VH> {

        private final List<Integer> sortedIndices;   // best→worst order

        PhotoStripAdapter(List<Integer> sortedIndices) {
            this.sortedIndices = sortedIndices;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater()
                .inflate(R.layout.item_burst_photo, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            int photoIdx = sortedIndices.get(position);
            Uri uri      = Uri.parse(cache.uriStrings.get(photoIdx));
            float score  = cache.scores.get(photoIdx);

            Glide.with(BurstResultsActivity.this)
                .load(uri)
                .placeholder(R.color.color_surface)
                .centerCrop()
                .into(holder.ivPhoto);

            holder.tvPhotoScore.setText(String.format(Locale.US, "%.2f", score));
        }

        @Override public int getItemCount() { return sortedIndices.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivPhoto;
            final TextView  tvPhotoScore;
            VH(android.view.View v) {
                super(v);
                ivPhoto      = v.findViewById(R.id.ivPhoto);
                tvPhotoScore = v.findViewById(R.id.tvPhotoScore);
            }
        }
    }
}
