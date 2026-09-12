package com.termux.app.terminal.suggestion;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.os.Build;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Connects detected commands to a compact floating UI for the active terminal session. */
public final class CommandSuggestionController {

    private static final long ENTER_DURATION = 220;
    private static final long EXIT_DURATION = 150;
    private static final long ICON_HALF_DURATION = 90;

    private final TermuxActivity mActivity;
    private final Map<TerminalSession, CommandSuggestionDetector> mDetectors = new IdentityHashMap<>();
    private final Map<TerminalSession, CommandSuggestionUiState> mStates = new IdentityHashMap<>();
    private final View mOverlay;
    private final MaterialCardView mPanel;
    private final NestedScrollView mScrollView;
    private final LinearLayout mSuggestionList;
    private final FloatingActionButton mFab;
    private final TextView mBadge;
    private AnimatorSet mAttentionAnimator;
    private int mIconAnimationGeneration;

    public CommandSuggestionController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        mOverlay = activity.findViewById(R.id.command_suggestion_overlay);
        mPanel = activity.findViewById(R.id.command_suggestion_panel);
        mScrollView = activity.findViewById(R.id.command_suggestion_scroll_view);
        mSuggestionList = activity.findViewById(R.id.command_suggestion_list);
        mFab = activity.findViewById(R.id.command_suggestion_fab);
        mBadge = activity.findViewById(R.id.command_suggestion_badge);
        mFab.setOnClickListener(view -> toggleExpanded());
    }

    public void onOutput(@NonNull TerminalSession session, @NonNull byte[] data, int count) {
        CommandSuggestionDetector detector = mDetectors.get(session);
        if (detector == null) {
            detector = new CommandSuggestionDetector();
            mDetectors.put(session, detector);
        }

        List<String> detected = detector.consume(data, count);
        if (detected.isEmpty()) return;

        CommandSuggestionUiState state = getOrCreateState(session);
        boolean added = false;
        for (String suggestion : detected) added |= state.add(suggestion);
        if (!added || session != mActivity.getCurrentSession() || !mActivity.isVisible()) return;

        if (state.isExpanded()) {
            renderSuggestionList(session, state, true);
        } else {
            showCollapsed(state, true);
        }
    }

    /** Collapse and render only the newly active session's suggestion state. */
    public void onSessionChanged() {
        for (CommandSuggestionUiState state : mStates.values()) state.setExpanded(false);
        dismiss();

        TerminalSession session = mActivity.getCurrentSession();
        CommandSuggestionUiState state = session == null ? null : mStates.get(session);
        if (state != null && state.hasSuggestions()) showCollapsed(state, false);
    }

    public void onSessionFinished(@NonNull TerminalSession session) {
        mDetectors.remove(session);
        mStates.remove(session);
        if (session == mActivity.getCurrentSession()) dismiss();
    }

    /** Hide transient views without discarding suggestions retained for a session. */
    public void dismiss() {
        cancelAnimations();
        mPanel.setVisibility(View.GONE);
        mPanel.setAlpha(1f);
        mPanel.setTranslationY(0f);
        mOverlay.setVisibility(View.GONE);
        mOverlay.setAlpha(1f);
        mFab.setAlpha(1f);
        mFab.setScaleX(1f);
        mFab.setScaleY(1f);
        mFab.setRotation(0f);
        mBadge.setAlpha(1f);
        mSuggestionList.removeAllViews();
    }

    private void toggleExpanded() {
        TerminalSession session = mActivity.getCurrentSession();
        CommandSuggestionUiState state = session == null ? null : mStates.get(session);
        if (state == null || !state.hasSuggestions()) return;

        if (state.isExpanded()) collapse(state);
        else expand(session, state);
    }

    private void expand(@NonNull TerminalSession session, @NonNull CommandSuggestionUiState state) {
        state.setExpanded(true);
        renderSuggestionList(session, state, false);
        updateFabAccessibility(state);
        animateFabIcon(R.drawable.ic_close);

        mPanel.animate().cancel();
        mPanel.setVisibility(View.VISIBLE);
        if (animationsEnabled()) {
            mPanel.setAlpha(0f);
            mPanel.setTranslationY(dp(16));
            mPanel.animate().alpha(1f).translationY(0f).setDuration(ENTER_DURATION)
                .setInterpolator(new DecelerateInterpolator()).start();
            animateRowsIn();
            hideBadge();
        } else {
            mPanel.setAlpha(1f);
            mPanel.setTranslationY(0f);
            mBadge.setVisibility(View.INVISIBLE);
            mBadge.setAlpha(0f);
        }
    }

    private void collapse(@NonNull CommandSuggestionUiState state) {
        state.setExpanded(false);
        updateFabAccessibility(state);
        animateFabIcon(R.drawable.ic_command_suggestion);
        showBadge(state, true);

        mPanel.animate().cancel();
        if (animationsEnabled() && mPanel.getVisibility() == View.VISIBLE) {
            mPanel.animate().alpha(0f).translationY(dp(12)).setDuration(EXIT_DURATION)
                .setInterpolator(new AccelerateInterpolator()).withEndAction(() -> {
                    mPanel.setVisibility(View.GONE);
                    mPanel.setAlpha(1f);
                    mPanel.setTranslationY(0f);
                    mSuggestionList.removeAllViews();
                }).start();
        } else {
            mPanel.setVisibility(View.GONE);
            mPanel.setAlpha(1f);
            mPanel.setTranslationY(0f);
            mSuggestionList.removeAllViews();
        }
    }

    private void showCollapsed(@NonNull CommandSuggestionUiState state, boolean animateAttention) {
        state.setExpanded(false);
        mPanel.setVisibility(View.GONE);
        mSuggestionList.removeAllViews();
        updateFabAccessibility(state);
        mFab.setImageResource(R.drawable.ic_command_suggestion);
        mFab.setRotation(0f);
        showBadge(state, false);

        boolean entering = mOverlay.getVisibility() != View.VISIBLE;
        mOverlay.setVisibility(View.VISIBLE);
        mOverlay.setAlpha(1f);
        if (animateAttention && animationsEnabled()) animateAttention(entering);
    }

    private void renderSuggestionList(@NonNull TerminalSession sourceSession,
                                      @NonNull CommandSuggestionUiState state,
                                      boolean animateNewest) {
        mSuggestionList.removeAllViews();
        for (String suggestion : state.getSuggestions())
            mSuggestionList.addView(createSuggestionRow(sourceSession, suggestion));
        updatePanelBounds(state.getSuggestionCount());

        if (animateNewest && animationsEnabled() && mSuggestionList.getChildCount() > 0) {
            View row = mSuggestionList.getChildAt(mSuggestionList.getChildCount() - 1);
            row.setAlpha(0f);
            row.setTranslationY(dp(8));
            row.animate().alpha(1f).translationY(0f).setDuration(ENTER_DURATION)
                .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    @NonNull
    private View createSuggestionRow(@NonNull TerminalSession sourceSession,
                                     @NonNull String suggestion) {
        View row = LayoutInflater.from(mActivity).inflate(
            R.layout.view_command_suggestion, mSuggestionList, false);
        TextView commandView = row.findViewById(R.id.command_suggestion_text);
        MaterialButton insertButton = row.findViewById(R.id.insert_command_suggestion_button);
        ImageButton removeButton = row.findViewById(R.id.remove_command_suggestion_button);

        commandView.setText(suggestion);
        insertButton.setContentDescription(mActivity.getString(
            R.string.action_insert_command_description, suggestion));
        insertButton.setOnClickListener(view -> insert(sourceSession, suggestion));
        removeButton.setContentDescription(mActivity.getString(
            R.string.action_remove_command_suggestion_description, suggestion));
        removeButton.setOnClickListener(view -> remove(sourceSession, suggestion, row));
        return row;
    }

    private void insert(@NonNull TerminalSession sourceSession,
                        @NonNull String suggestion) {
        if (mActivity.getCurrentSession() != sourceSession || !sourceSession.isRunning()) return;
        TerminalEmulator emulator = sourceSession.getEmulator();
        if (emulator == null) return;

        emulator.paste(suggestion);
        removeSuggestion(sourceSession, suggestion);
        mActivity.getTerminalView().requestFocus();
    }

    private void remove(@NonNull TerminalSession sourceSession,
                        @NonNull String suggestion, @NonNull View row) {
        if (mActivity.getCurrentSession() != sourceSession) return;
        CommandSuggestionUiState state = mStates.get(sourceSession);
        if (state == null || !state.remove(suggestion)) return;

        if (!state.hasSuggestions()) {
            mStates.remove(sourceSession);
            hideAll();
            return;
        }

        updateFabAccessibility(state);
        if (animationsEnabled()) {
            row.animate().alpha(0f).translationX(dp(16)).setDuration(EXIT_DURATION)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> renderSuggestionList(sourceSession, state, false)).start();
        } else {
            renderSuggestionList(sourceSession, state, false);
        }
    }

    private void removeSuggestion(@NonNull TerminalSession session,
                                  @NonNull String suggestion) {
        CommandSuggestionUiState state = mStates.get(session);
        if (state == null || !state.remove(suggestion)) return;

        if (!state.hasSuggestions()) {
            mStates.remove(session);
            hideAll();
        } else {
            collapse(state);
        }
    }

    private void hideAll() {
        cancelAnimations();
        mIconAnimationGeneration++;
        if (animationsEnabled() && mOverlay.getVisibility() == View.VISIBLE) {
            mOverlay.animate().alpha(0f).setDuration(EXIT_DURATION)
                .setInterpolator(new AccelerateInterpolator()).withEndAction(this::dismiss).start();
            mFab.animate().scaleX(0.8f).scaleY(0.8f).setDuration(EXIT_DURATION).start();
        } else {
            dismiss();
        }
    }

    private void updateFabAccessibility(@NonNull CommandSuggestionUiState state) {
        if (state.isExpanded()) {
            mFab.setContentDescription(mActivity.getString(R.string.action_close_command_suggestions));
        } else {
            mFab.setContentDescription(mActivity.getResources().getQuantityString(
                R.plurals.command_suggestions_available_description,
                state.getSuggestionCount(), state.getSuggestionCount()));
        }
    }

    private void showBadge(@NonNull CommandSuggestionUiState state, boolean animate) {
        mBadge.animate().cancel();
        mBadge.setText(state.getBadgeText());
        mBadge.setVisibility(View.VISIBLE);
        if (animate && animationsEnabled()) {
            mBadge.setAlpha(0f);
            mBadge.setScaleX(0.7f);
            mBadge.setScaleY(0.7f);
            mBadge.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ENTER_DURATION)
                .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            mBadge.setAlpha(1f);
            mBadge.setScaleX(1f);
            mBadge.setScaleY(1f);
        }
    }

    private void hideBadge() {
        mBadge.animate().cancel();
        mBadge.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(EXIT_DURATION)
            .withEndAction(() -> mBadge.setVisibility(View.INVISIBLE)).start();
    }

    private void animateAttention(boolean entering) {
        if (mAttentionAnimator != null) mAttentionAnimator.cancel();
        float startScale = entering ? 0.8f : 1f;
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(
            View.SCALE_X, startScale, 1.12f, 1f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(
            View.SCALE_Y, startScale, 1.12f, 1f);
        ObjectAnimator fabPulse = ObjectAnimator.ofPropertyValuesHolder(mFab, scaleX, scaleY);
        fabPulse.setDuration(260);
        fabPulse.setInterpolator(new DecelerateInterpolator());

        ObjectAnimator fabFade = ObjectAnimator.ofFloat(mFab, View.ALPHA,
            entering ? 0f : 1f, 1f);
        fabFade.setDuration(ENTER_DURATION);
        ObjectAnimator badgePop = ObjectAnimator.ofPropertyValuesHolder(mBadge,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.7f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.7f, 1f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f));
        badgePop.setDuration(ENTER_DURATION);
        badgePop.setInterpolator(new DecelerateInterpolator());

        mAttentionAnimator = new AnimatorSet();
        mAttentionAnimator.playTogether(fabPulse, fabFade, badgePop);
        mAttentionAnimator.start();
    }

    private void animateFabIcon(@DrawableRes int icon) {
        int generation = ++mIconAnimationGeneration;
        mFab.animate().cancel();
        if (!animationsEnabled()) {
            mFab.setImageResource(icon);
            mFab.setRotation(0f);
            mFab.setAlpha(1f);
            return;
        }

        mFab.animate().rotation(90f).alpha(0.72f).setDuration(ICON_HALF_DURATION)
            .withEndAction(() -> {
                if (generation != mIconAnimationGeneration) return;
                mFab.setImageResource(icon);
                mFab.setRotation(-90f);
                mFab.animate().rotation(0f).alpha(1f).setDuration(ICON_HALF_DURATION)
                    .setInterpolator(new DecelerateInterpolator()).start();
            }).start();
    }

    private void animateRowsIn() {
        int count = Math.min(mSuggestionList.getChildCount(), 5);
        for (int i = 0; i < count; i++) {
            View row = mSuggestionList.getChildAt(i);
            row.setAlpha(0f);
            row.setTranslationY(dp(8));
            row.animate().alpha(1f).translationY(0f).setStartDelay(i * 35L)
                .setDuration(ENTER_DURATION).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void updatePanelBounds(int suggestionCount) {
        int overlayWidth = mOverlay.getWidth();
        if (overlayWidth > 0) {
            ViewGroup.LayoutParams panelParams = mPanel.getLayoutParams();
            panelParams.width = Math.min(dp(360), overlayWidth);
            mPanel.setLayoutParams(panelParams);
        }

        ViewGroup.LayoutParams scrollParams = mScrollView.getLayoutParams();
        if (suggestionCount > 3) {
            int terminalHeight = mActivity.getTerminalView().getHeight();
            scrollParams.height = Math.min(dp(240), Math.max(dp(96), terminalHeight - dp(200)));
        } else {
            scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        mScrollView.setLayoutParams(scrollParams);
    }

    private void cancelAnimations() {
        if (mAttentionAnimator != null) {
            mAttentionAnimator.cancel();
            mAttentionAnimator = null;
        }
        mOverlay.animate().cancel();
        mPanel.animate().cancel();
        mFab.animate().cancel();
        mBadge.animate().cancel();
        for (int i = 0; i < mSuggestionList.getChildCount(); i++)
            mSuggestionList.getChildAt(i).animate().cancel();
    }

    private boolean animationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return ValueAnimator.areAnimatorsEnabled();
        return Settings.Global.getFloat(mActivity.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
    }

    @NonNull
    private CommandSuggestionUiState getOrCreateState(@NonNull TerminalSession session) {
        CommandSuggestionUiState state = mStates.get(session);
        if (state == null) {
            state = new CommandSuggestionUiState();
            mStates.put(session, state);
        }
        return state;
    }

    private int dp(int value) {
        return Math.round(value * mActivity.getResources().getDisplayMetrics().density);
    }
}
