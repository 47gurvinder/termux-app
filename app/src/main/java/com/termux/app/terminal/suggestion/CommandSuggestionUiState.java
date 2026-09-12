package com.termux.app.terminal.suggestion;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mutable presentation state for one terminal session's suggested commands. */
final class CommandSuggestionUiState {

    private final List<String> mSuggestions = new ArrayList<>();
    private boolean mExpanded;

    boolean add(@NonNull String suggestion) {
        if (mSuggestions.contains(suggestion)) return false;
        mSuggestions.add(suggestion);
        return true;
    }

    boolean remove(@NonNull String command) {
        return mSuggestions.remove(command);
    }

    @NonNull
    List<String> getSuggestions() {
        return Collections.unmodifiableList(mSuggestions);
    }

    int getSuggestionCount() {
        return mSuggestions.size();
    }

    boolean hasSuggestions() {
        return !mSuggestions.isEmpty();
    }

    boolean isExpanded() {
        return mExpanded && hasSuggestions();
    }

    void setExpanded(boolean expanded) {
        mExpanded = expanded && hasSuggestions();
    }

    @NonNull
    String getBadgeText() {
        int count = getSuggestionCount();
        return count > 99 ? "99+" : Integer.toString(count);
    }
}
