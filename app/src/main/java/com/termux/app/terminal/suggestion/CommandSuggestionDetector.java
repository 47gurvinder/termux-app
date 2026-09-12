package com.termux.app.terminal.suggestion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Streaming detector for high-confidence output from Termux's command-not-found utility. */
final class CommandSuggestionDetector {

    private static final int MAX_LINE_LENGTH = 512;

    private static final Pattern HEADER_PATTERN = Pattern.compile(
        "The program (?:'[A-Za-z0-9._+:-]+'|[A-Za-z0-9._+:-]+) is not installed\\. Install it by executing:");
    private static final Pattern COMMAND_PATTERN = Pattern.compile(" ?pkg install [a-z0-9][a-z0-9+.-]*");

    private final StringBuilder mLine = new StringBuilder();
    private boolean mWaitingForCommand;
    private boolean mWaitingForAlternative;
    private boolean mInvalidLine;
    private boolean mCarriageReturnPending;

    /** Consume subprocess output. Returned suggestions are ordered as they occur in the chunk. */
    @NonNull
    List<String> consume(@NonNull byte[] data, int count) {
        if (count < 0 || count > data.length)
            throw new IllegalArgumentException("Invalid byte count: " + count);
        if (count == 0) return Collections.emptyList();

        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int value = data[i] & 0xff;
            if (value == '\r') {
                mCarriageReturnPending = true;
                continue;
            }

            if (mCarriageReturnPending) {
                mCarriageReturnPending = false;
                if (value != '\n') {
                    // A bare carriage return moves the cursor to the start of the line. Discard
                    // content that subsequent output overwrites, including shell control sequences.
                    mLine.setLength(0);
                    mInvalidLine = false;
                }
            }

            if (value == '\n') {
                String suggestion = finishLine();
                if (suggestion != null) suggestions.add(suggestion);
            } else if (value < 0x20 || value > 0x7e) {
                // Supported command-not-found output is ASCII. Reject controls, ANSI escapes and
                // non-ASCII bytes without attempting to normalize terminal output.
                mInvalidLine = true;
            } else if (mLine.length() < MAX_LINE_LENGTH) {
                mLine.append((char) value);
            } else {
                mInvalidLine = true;
            }
        }
        return suggestions;
    }

    @Nullable
    private String finishLine() {
        String line = mLine.toString();
        boolean invalidLine = mInvalidLine;
        mLine.setLength(0);
        mInvalidLine = false;

        if (mWaitingForCommand) {
            mWaitingForCommand = false;
            if (!invalidLine && COMMAND_PATTERN.matcher(line).matches()) {
                String command = line.charAt(0) == ' ' ? line.substring(1) : line;
                mWaitingForAlternative = true;
                return command;
            }
        }

        if (mWaitingForAlternative) {
            mWaitingForAlternative = false;
            if (!invalidLine && "or".equals(line)) {
                mWaitingForCommand = true;
                return null;
            }
        }

        mWaitingForCommand = !invalidLine && HEADER_PATTERN.matcher(line).matches();
        return null;
    }
}
