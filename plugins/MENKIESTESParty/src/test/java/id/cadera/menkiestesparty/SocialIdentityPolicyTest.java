package id.cadera.menkiestesparty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SocialIdentityPolicyTest {

    @Test
    void descriptionSanitizationRemovesFormattingAndNormalizesWhitespace() {
        assertEquals("Hello world", SocialIdentityPolicy.sanitizeDescription("&aHello\n   world", 120));
        assertEquals("12345", SocialIdentityPolicy.sanitizeDescription("123456789", 5));
        assertEquals("", SocialIdentityPolicy.sanitizeDescription(null, 120));
    }

    @Test
    void tagsAreUppercaseAsciiAndLengthBounded() {
        assertEquals("MENKI", SocialIdentityPolicy.normalizeTag("menki", 2, 5));
        assertEquals("A1", SocialIdentityPolicy.normalizeTag("a1", 2, 5));
        assertNull(SocialIdentityPolicy.normalizeTag("A-B", 2, 5));
        assertNull(SocialIdentityPolicy.normalizeTag("A", 2, 5));
        assertNull(SocialIdentityPolicy.normalizeTag("TOOLONG", 2, 5));
    }

    @Test
    void memberActivityUsesBoundedAgeWindows() {
        long now = 1_000_000_000L;
        assertEquals("ONLINE", SocialIdentityPolicy.activityStatus(true, 0L, now, 7, 30));
        assertEquals("ACTIVE", SocialIdentityPolicy.activityStatus(false, now - 2L * 86_400_000L, now, 7, 30));
        assertEquals("AWAY", SocialIdentityPolicy.activityStatus(false, now - 14L * 86_400_000L, now, 7, 30));
        assertEquals("INACTIVE", SocialIdentityPolicy.activityStatus(false, now - 40L * 86_400_000L, now, 7, 30));
        assertEquals("INACTIVE", SocialIdentityPolicy.activityStatus(false, 0L, now, 7, 30));
    }

    @Test
    void achievementsAndLeaderboardUseDeterministicMetrics() {
        SocialIdentityPolicy.Metrics metrics = new SocialIdentityPolicy.Metrics(4, 1800, 7, 12, 45, 1500);
        assertTrue(SocialIdentityPolicy.achievementUnlocked("level", 4, metrics));
        assertTrue(SocialIdentityPolicy.achievementUnlocked("reputation", 1500, metrics));
        assertTrue(SocialIdentityPolicy.achievementUnlocked("members", 5, metrics));
        assertTrue(SocialIdentityPolicy.achievementUnlocked("projects", 10, metrics));
        assertTrue(SocialIdentityPolicy.achievementUnlocked("age_days", 30, metrics));
        assertTrue(SocialIdentityPolicy.achievementUnlocked("activity", 1000, metrics));
        assertFalse(SocialIdentityPolicy.achievementUnlocked("level", 5, metrics));
        assertFalse(SocialIdentityPolicy.achievementUnlocked("unknown", 1, metrics));

        assertEquals(1800L, SocialIdentityPolicy.leaderboardScore("reputation", metrics));
        assertEquals(4L, SocialIdentityPolicy.leaderboardScore("level", metrics));
        assertEquals(7L, SocialIdentityPolicy.leaderboardScore("members", metrics));
        assertEquals(12L, SocialIdentityPolicy.leaderboardScore("projects", metrics));
        assertEquals(45L, SocialIdentityPolicy.leaderboardScore("age", metrics));
        assertEquals(1500L, SocialIdentityPolicy.leaderboardScore("activity", metrics));
    }
}
