package com.zs.wordecho.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.zs.wordecho.R;
import com.zs.wordecho.data.FavoriteStore;
import com.zs.wordecho.data.Word;
import com.zs.wordecho.data.WordRepository;

import java.util.List;

public final class SavedPage extends FrameLayout {
    public interface Listener {
        void onWordClick(Word word);
        void onSpeak(Word word);
        void onToggleFavorite(Word word);
        void onReview();
        void onBrowse();
    }

    private final WordRepository repository;
    private final FavoriteStore favorites;
    private final Listener listener;
    private final RecyclerView recyclerView;
    private final WordAdapter adapter;
    private final TextView savedCount;
    private final View emptyState;
    private final MaterialCardView reviewCard;
    private final TextView savedSubtitle;

    public SavedPage(Context context, FavoriteStore favorites, Listener listener) {
        super(context);
        this.repository = WordRepository.getInstance(context);
        this.favorites = favorites;
        this.listener = listener;
        LayoutInflater.from(context).inflate(R.layout.view_saved, this, true);

        recyclerView = findViewById(R.id.saved_list);
        savedCount = findViewById(R.id.saved_count);
        emptyState = findViewById(R.id.saved_empty);
        reviewCard = findViewById(R.id.review_card);
        savedSubtitle = findViewById(R.id.saved_subtitle);

        adapter = new WordAdapter(favorites, new WordAdapter.Listener() {
            @Override
            public void onWordClick(Word word) {
                listener.onWordClick(word);
            }

            @Override
            public void onSpeak(Word word) {
                listener.onSpeak(word);
            }

            @Override
            public void onToggleFavorite(Word word) {
                listener.onToggleFavorite(word);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(null);

        findViewById(R.id.btn_review).setOnClickListener(v -> listener.onReview());
        findViewById(R.id.btn_browse_words).setOnClickListener(v -> listener.onBrowse());

        refresh();
    }

    public void refresh() {
        if (!repository.isLoaded()) {
            savedCount.setText("—");
            return;
        }
        List<Word> words = repository.getFavorites(favorites.getKeys());
        adapter.submitList(words);
        savedCount.setText(getContext().getString(R.string.saved_count, words.size()));
        boolean empty = words.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        reviewCard.setVisibility(empty ? View.GONE : View.VISIBLE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        savedSubtitle.setText(empty ? getContext().getString(R.string.saved_empty_body)
                : getContext().getString(R.string.saved_subtitle));
    }

    public int favoriteCount() {
        return favorites.size();
    }
}
