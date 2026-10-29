package com.zs.wordecho.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.zs.wordecho.R;
import com.zs.wordecho.data.FavoriteStore;
import com.zs.wordecho.data.Word;

public final class WordDetailSheet {
    public interface Listener {
        void onFavoriteChanged(Word word, boolean favorite);
        void onListen(Word word);
    }

    private WordDetailSheet() {
    }

    public static void show(Activity activity, Word word, FavoriteStore favorites, Listener listener) {
        if (activity.isFinishing() || word == null) {
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.sheet_word_detail, null, false);
        dialog.setContentView(view);

        TextView level = view.findViewById(R.id.detail_level);
        TextView wordText = view.findViewById(R.id.detail_word);
        TextView pos = view.findViewById(R.id.detail_pos);
        TextView meaning = view.findViewById(R.id.detail_meaning);
        MaterialButton favorite = view.findViewById(R.id.detail_favorite);
        MaterialButton listen = view.findViewById(R.id.detail_listen);

        UiUtils.applyLevelTag(level, word.getLevelMask());
        wordText.setText(word.getText());
        if (word.getPosLabel().isEmpty()) {
            pos.setVisibility(View.GONE);
        } else {
            pos.setText(word.getPosAbbrev() + "  ·  " + word.getPosLabel());
        }
        meaning.setText(word.getMeaningBody());

        final boolean[] currentFavorite = {favorites.isFavorite(word)};
        renderFavorite(favorite, currentFavorite[0]);
        favorite.setOnClickListener(v -> {
            currentFavorite[0] = favorites.toggle(word);
            renderFavorite(favorite, currentFavorite[0]);
            listener.onFavoriteChanged(word, currentFavorite[0]);
        });

        listen.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onListen(word);
        });
        view.findViewById(R.id.detail_close).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void renderFavorite(MaterialButton button, boolean favorite) {
        button.setText(favorite ? R.string.detail_unfavorite : R.string.detail_favorite);
        button.setIconResource(favorite ? R.drawable.ic_star_filled : R.drawable.ic_star);
    }
}
