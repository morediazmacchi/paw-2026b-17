package ar.edu.itba.paw.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchTermsTest {

    @Test
    public void testParseLowercasesAndSplitsOnSpaces() {
        assertEquals(Arrays.asList("recital", "de", "rock"), SearchTerms.parse("Recital DE Rock"));
    }

    @Test
    public void testParseRemovesAccentsAndTurnsEnyeIntoN() {
        assertEquals(Arrays.asList("musica", "ano", "pinguino", "cafe"), SearchTerms.parse("Música Año Pingüino CAFÉ"));
    }

    @Test
    public void testParseTreatsPunctuationAsASeparator() {
        assertEquals(Arrays.asList("rock", "roll", "jazz", "vivo"), SearchTerms.parse("rock&roll, jazz! (vivo)"));
    }

    @Test
    public void testParseDropsTheFinalSOfAPlural() {
        assertEquals(Arrays.asList("banda", "event", "bar"), SearchTerms.parse("bandas events bars"));
    }

    @Test
    public void testParseDropsOnlyTheFinalS() {
        assertEquals(Collections.singletonList("recitale"), SearchTerms.parse("recitales"));
    }

    @Test
    public void testParseKeepsAShortWordThatEndsInS() {
        assertEquals(Arrays.asList("bus", "mas"), SearchTerms.parse("bus mas"));
    }

    @Test
    public void testParseCountsAWordAndItsPluralOnce() {
        assertEquals(Collections.singletonList("rock"), SearchTerms.parse("rock rocks"));
    }

    @Test
    public void testParseKeepsDigits() {
        assertEquals(Arrays.asList("futbol", "5"), SearchTerms.parse("Fútbol 5"));
    }

    @Test
    public void testParseOfNullIsEmpty() {
        assertEquals(Collections.emptyList(), SearchTerms.parse(null));
    }

    @Test
    public void testParseOfBlankIsEmpty() {
        assertEquals(Collections.emptyList(), SearchTerms.parse("   \t "));
    }

    @Test
    public void testParseOfOnlySignsIsEmpty() {
        assertEquals(Collections.emptyList(), SearchTerms.parse("¿?!... --- %%"));
    }

    @Test
    public void testParseCountsARepeatedWordOnce() {
        assertEquals(Arrays.asList("rock", "vivo"), SearchTerms.parse("rock ROCK Vivo rock"));
    }

    @Test
    public void testParseNeverLeavesLikeWildcardsInAWord() {
        assertEquals(Collections.singletonList("100"), SearchTerms.parse("100%_\\"));
    }

    @Test
    public void testParseKeepsOnlyTheFirstWordsWhenThereAreTooMany() {
        assertEquals(Arrays.asList("a1", "b2", "c3", "d4", "e5", "f6"), SearchTerms.parse("a1 b2 c3 d4 e5 f6 g7 h8"));
    }

    @Test
    public void testParseCutsAWordThatIsTooLong() {
        final String longWord = String.join("", Collections.nCopies(SearchTerms.MAX_TERM_LENGTH + 10, "x"));
        assertEquals(Collections.singletonList(longWord.substring(0, SearchTerms.MAX_TERM_LENGTH)),
                SearchTerms.parse(longWord));
    }

    @Test
    public void testParseIgnoresTheTextBeyondTheMaximumLength() {
        final String filler = String.join("", Collections.nCopies(SearchTerms.MAX_TEXT_LENGTH, " "));
        assertEquals(Collections.singletonList("rock"), SearchTerms.parse("rock" + filler + "jazz"));
    }

    @Test
    public void testAWordOfFourLettersToleratesTypos() {
        assertTrue(SearchTerms.toleratesTypos("rock"));
    }

    @Test
    public void testAWordOfThreeLettersDoesNotTolerateTypos() {
        assertFalse(SearchTerms.toleratesTypos("rok"));
    }
}
