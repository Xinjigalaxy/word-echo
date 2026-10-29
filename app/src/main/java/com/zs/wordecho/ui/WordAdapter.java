package com.zs.wordecho.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zs.wordecho.R;
import com.zs.wordecho.data.FavoriteStore;
import com.zs.wordecho.data.Word;

public final class WordAdapter extends ListAdapter<Word, WordAdapter.WordViewHolder> {
    public interface Listener {
        void onWordClick(Word word);
        void onSpeak(Word word);
        void onToggleFavorite(Word word);
    }

    private static final DiffUtil.ItemCallback<Word> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Word oldItem, @NonNull Word newItem) {
            return oldItem.getKey().equals(newItem.getKey());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Word oldItem, @NonNull Word newItem) {
            return oldItem.getKey().equals(newItem.getKey())
                    && oldItem.getMeaning().equals(newItem.getMeaning())
                    && oldItem.getLevelMask() == newItem.getLevelMask();
        }
    };

    private final FavoriteStore favorites;
    private final Listener listener;

    public WordAdapter(FavoriteStore favorites, Listener listener) {
        super(DIFF);
        this.favorites = favorites;
        this.listener = listener;
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        Word word = getItem(position);
        holder.bind(word);
    }

    final class WordViewHolder extends RecyclerView.ViewHolder {
        private final TextView level;
        private final TextView text;
        private final TextView meaning;
        private final ImageButton speak;
        private final ImageButton star;

        WordViewHolder(@NonNull View itemView) {
            super(itemView);
            level = itemView.findViewById(R.id.item_level);
            text = itemView.findViewById(R.id.item_word);
            meaning = itemView.findViewById(R.id.item_meaning);
            speak = itemView.findViewById(R.id.item_speak);
            star = itemView.findViewById(R.id.item_star);
        }

        void bind(Word word) {
            UiUtils.applyLevelTag(level, word.getLevelMask());
            text.setText(word.getText());
            meaning.setText(word.getMeaningBody());
            boolean favorite = favorites.isFavorite(word);
            star.setSelected(favorite);
            star.setImageResource(favorite ? R.drawable.ic_star_filled : R.drawable.ic_star);
            star.setContentDescription(itemView.getContext().getString(
                    favorite ? R.string.unfavorite : R.string.favorite));

            itemView.setOnClickListener(v -> listener.onWordClick(word));
            speak.setOnClickListener(v -> listener.onSpeak(word));
            star.setOnClickListener(v -> listener.onToggleFavorite(word));
        }
    }
}
