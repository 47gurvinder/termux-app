package com.termux.app.terminal.suggestion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommandSuggestionUiStateTest {

    private final CommandSuggestionUiState mState = new CommandSuggestionUiState();

    @Test
    public void fabIsHiddenWithoutSuggestions() {
        assertFalse(mState.hasSuggestions());
        assertFalse(mState.isExpanded());
    }

    @Test
    public void addingSuggestionsUpdatesBadgeAndIgnoresDuplicates() {
        assertTrue(mState.add("pkg install dropbear"));
        assertFalse(mState.add("pkg install dropbear"));
        assertTrue(mState.add("pkg install openssh"));

        assertTrue(mState.hasSuggestions());
        assertEquals(2, mState.getSuggestionCount());
        assertEquals("2", mState.getBadgeText());
    }

    @Test
    public void expandAndCollapseRequireSuggestions() {
        mState.setExpanded(true);
        assertFalse(mState.isExpanded());

        mState.add("pkg install openssh");
        mState.setExpanded(true);
        assertTrue(mState.isExpanded());

        mState.setExpanded(false);
        assertFalse(mState.isExpanded());
    }

    @Test
    public void removingLastSuggestionHidesAndCollapsesUi() {
        mState.add("pkg install openssh");
        mState.setExpanded(true);

        assertTrue(mState.remove("pkg install openssh"));
        assertFalse(mState.hasSuggestions());
        assertFalse(mState.isExpanded());
    }

    @Test
    public void separateStatesDoNotShareSuggestions() {
        CommandSuggestionUiState otherSession = new CommandSuggestionUiState();
        mState.add("pkg install openssh");

        assertTrue(mState.hasSuggestions());
        assertFalse(otherSession.hasSuggestions());
    }
}
