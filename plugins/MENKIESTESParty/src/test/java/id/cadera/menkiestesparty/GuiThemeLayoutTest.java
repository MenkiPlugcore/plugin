package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiThemeLayoutTest {

    @Test
    void twentySevenSlotLayoutUsesStableAccentPattern() {
        assertEquals(GuiThemeLayout.Tone.SECONDARY, GuiThemeLayout.tone(27, 0));
        assertEquals(GuiThemeLayout.Tone.ACCENT, GuiThemeLayout.tone(27, 1));
        assertEquals(GuiThemeLayout.Tone.ACCENT, GuiThemeLayout.tone(27, 4));
        assertEquals(GuiThemeLayout.Tone.PRIMARY, GuiThemeLayout.tone(27, 10));
        assertEquals(GuiThemeLayout.Tone.ACCENT, GuiThemeLayout.tone(27, 22));
        assertEquals(GuiThemeLayout.Tone.SECONDARY, GuiThemeLayout.tone(27, 26));
    }

    @Test
    void fiftyFourSlotLayoutNeverNeedsRuntimeState() {
        assertEquals(GuiThemeLayout.Tone.SECONDARY, GuiThemeLayout.tone(54, 0));
        assertEquals(GuiThemeLayout.Tone.ACCENT, GuiThemeLayout.tone(54, 4));
        assertEquals(GuiThemeLayout.Tone.SECONDARY, GuiThemeLayout.tone(54, 18));
        assertEquals(GuiThemeLayout.Tone.PRIMARY, GuiThemeLayout.tone(54, 27));
        assertEquals(GuiThemeLayout.Tone.ACCENT, GuiThemeLayout.tone(54, 49));
        assertEquals(GuiThemeLayout.Tone.SECONDARY, GuiThemeLayout.tone(54, 53));
    }

    @Test
    void invalidInputsFailToPrimaryRatherThanThrowing() {
        assertEquals(GuiThemeLayout.Tone.PRIMARY, GuiThemeLayout.tone(0, 0));
        assertEquals(GuiThemeLayout.Tone.PRIMARY, GuiThemeLayout.tone(27, -1));
        assertEquals(GuiThemeLayout.Tone.PRIMARY, GuiThemeLayout.tone(27, 27));
    }
}
