package com.termux.app.terminal.suggestion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class CommandSuggestionDetectorTest {

    private final CommandSuggestionDetector mDetector = new CommandSuggestionDetector();

    @Test
    public void detectsSimpleSuggestion() {
        List<String> suggestions = consume(
            "The program ssh is not installed. Install it by executing:\n" +
            " pkg install openssh\n");

        assertEquals(1, suggestions.size());
        assertEquals("pkg install openssh", suggestions.get(0));
    }

    @Test
    public void detectsQuotedProgramName() {
        List<String> suggestions = consume(
            "The program 'ssh' is not installed. Install it by executing:\n" +
            "pkg install openssh\n");

        assertEquals(1, suggestions.size());
    }

    @Test
    public void ignoresUnrelatedOutput() {
        assertTrue(consume("Run this if you want:\npkg install openssh\n").isEmpty());
    }

    @Test
    public void ignoresMalformedSuggestion() {
        assertTrue(consume(
            "The program ssh is not installed; Install it by executing:\n" +
            " pkg install openssh\n").isEmpty());
    }

    @Test
    public void detectsSuggestionAcrossChunks() {
        assertTrue(consume("The program ss").isEmpty());
        assertTrue(consume("h is not installed. Install it by executing:\n pkg ins").isEmpty());

        List<String> suggestions = consume("tall openssh\n");
        assertEquals(1, suggestions.size());
        assertEquals("pkg install openssh", suggestions.get(0));
    }

    @Test
    public void ignoresAnsiCorruptedSuggestion() {
        assertTrue(consume(
            "The program \u001b[31mssh\u001b[0m is not installed. Install it by executing:\n" +
            " pkg install openssh\n").isEmpty());
    }

    @Test
    public void detectsSuggestionAfterCarriageReturnOverwritesShellControlSequence() {
        List<String> suggestions = consume(
            "\u001b[?2004l\rThe program ssh is not installed. Install it by executing:\r\n" +
            " pkg install openssh\r\n");

        assertEquals(1, suggestions.size());
        assertEquals("pkg install openssh", suggestions.get(0));
    }

    @Test
    public void detectsMultipleSuggestions() {
        List<String> suggestions = consume(
            "The program ssh is not installed. Install it by executing:\n" +
            " pkg install openssh\n" +
            "The program wget is not installed. Install it by executing:\n" +
            " pkg install wget\n");

        assertEquals(2, suggestions.size());
        assertEquals("pkg install openssh", suggestions.get(0));
        assertEquals("pkg install wget", suggestions.get(1));
    }

    @Test
    public void detectsAlternativeCommandsInOneSuggestion() {
        List<String> suggestions = consume(
            "The program ssh is not installed. Install it by executing:\n" +
            " pkg install dropbear\n" +
            "or\n" +
            " pkg install openssh\n");

        assertEquals(2, suggestions.size());
        assertEquals("pkg install dropbear", suggestions.get(0));
        assertEquals("pkg install openssh", suggestions.get(1));
    }

    @Test
    public void ignoresMalformedAlternativeCommand() {
        List<String> suggestions = consume(
            "The program ssh is not installed. Install it by executing:\n" +
            " pkg install dropbear\n" +
            "or\n" +
            " pkg install openssh; reboot\n");

        assertEquals(1, suggestions.size());
        assertEquals("pkg install dropbear", suggestions.get(0));
    }

    @Test
    public void ignoresRepositoryPrerequisiteSuggestion() {
        assertTrue(consume(
            "The program i3 is not installed. Install it by executing:\n" +
            " pkg install i3, after running pkg install x11-repo\n").isEmpty());
    }

    @Test
    public void ignoresUnexpectedCommandCharacters() {
        assertTrue(consume(
            "The program ssh is not installed. Install it by executing:\n" +
            " pkg install openssh; reboot\n").isEmpty());
    }

    @Test
    public void malformedCommandDoesNotPoisonNextSuggestion() {
        assertTrue(consume(
            "The program bad is not installed. Install it by executing:\n" +
            " pkg install bad$package\n").isEmpty());

        assertEquals(1, consume(
            "The program ssh is not installed. Install it by executing:\n" +
            " pkg install openssh\n").size());
    }

    private List<String> consume(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return mDetector.consume(bytes, bytes.length);
    }
}
