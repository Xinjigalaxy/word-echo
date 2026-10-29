package com.zs.wordecho.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.zs.wordecho.R;
import com.zs.wordecho.data.FavoriteStore;
import com.zs.wordecho.data.Word;
import com.zs.wordecho.data.WordRepository;

import java.util.List;

public final class BrowsePage extends FrameLayout {
    public interface Listener {
        void onWordClick(Word word);
        void onSpeak(Word word);
        void onToggleFavorite(Word word);
    }

    private final WordRepository repository;
    private final FavoriteStore favorites;
    private final Listener listener;
    private final RecyclerView recyclerView;
    private final WordAdapter adapter;
    private final TextView resultCount;
    private final View emptyState;
    private final TextInputEditText searchInput;
    private final ChipGroup filterGroup;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int levelFilter;
    private Runnable pendingSearch;

    public BrowsePage(Context context, FavoriteStore favorites, Listener listener) {
        super(context);
        this.repository = WordRepository.getInstance(context);
        this.favorites = favorites;
        this.listener = listener;
        LayoutInflater.from(context).inflate(R.layout.view_browse, this, true);

        recyclerView = findViewById(R.id.word_list);
        resultCount = findViewById(R.id.result_count);
        emptyState = findViewById(R.id.empty_state);
        searchInput = findViewById(R.id.search_input);
        filterGroup = findViewById(R.id.filter_group);

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
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard();
                    searchInput.clearFocus();
                }
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch();
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSearch = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (isSearch) {
                scheduleSearch();
                searchInput.clearFocus();
                hideKeyboard();
                return true;
            }
            return false;
        });

        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.chip_four) {
                levelFilter = 1;
            } else if (id == R.id.chip_six) {
                levelFilter = 2;
            } else {
                levelFilter = 0;
            }
            searchNow();
        });

        refresh();
    }

    private void scheduleSearch() {
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        pendingSearch = this::searchNow;
        handler.postDelayed(pendingSearch, 160L);
    }

    private void searchNow() {
        if (!repository.isLoaded()) {
            resultCount.setText(R.string.browse_empty);
            emptyState.setVisibility(View.GONE);
            return;
        }
        String query = searchInput.getText() == null ? "" : searchInput.getText().toString();
        List<Word> words = repository.search(query, levelFilter);
        adapter.submitList(words);
        resultCount.setText(getContext().getString(R.string.result_count,
                UiUtils.formatNumber(words.size())));
        emptyState.setVisibility(words.isEmpty() ? View.VISIBLE : View.GONE);
    }

    public void refresh() {
        if (!repository.isLoaded()) {
            resultCount.setText(R.string.browse_empty);
            return;
        }
        searchNow();
    }

    public void refreshFavorites() {
        if (adapter.getItemCount() > 0) {
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    public void focusSearch() {
        searchInput.requestFocus();
        searchInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 160L);
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    public void clearSearch() {
        searchInput.setText("");
        searchInput.clearFocus();
    }
}
