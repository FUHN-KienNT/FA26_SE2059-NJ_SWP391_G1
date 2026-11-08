package vn.edu.fpt.ams.util;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupScoreRulesTest {
    private BigDecimal n(String number) { return new BigDecimal(number); }

    @Test void weightedScoreUsesAvailableAverageAndCorrectDenominator() {
        assertEquals(n("8.30"),GroupScoreRules.finalScoreFromTotals(n("8"),n("18"),2,n("70"),n("30")));
        assertEquals(n("5.60"),GroupScoreRules.finalScoreFromTotals(n("8"),BigDecimal.ZERO,0,n("70"),n("30")));
        assertEquals(n("9.00"),GroupScoreRules.finalScoreFromTotals(n("8"),n("18"),2,n("0"),n("100")));
        assertEquals(n("8.00"),GroupScoreRules.finalScoreFromTotals(n("8"),n("18"),2,n("100"),n("0")));
    }

    @Test void finalHalfUpRoundingDoesNotRoundThePeerMeanFirst() {
        assertEquals(n("0.02"),GroupScoreRules.finalScoreFromTotals(n("0"),n("1"),6,n("91"),n("9")));
        assertEquals(n("0.01"),GroupScoreRules.finalScoreFromTotals(n("0"),n("0.99"),6,n("91"),n("9")));
    }

    @Test void invalidWeightsAndCountsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->GroupScoreRules.finalScoreFromTotals(n("8"),n("9"),1,n("70"),n("20")));
        assertThrows(IllegalArgumentException.class,()->GroupScoreRules.finalScoreFromTotals(n("8"),n("9"),1,n("-1"),n("101")));
        assertThrows(IllegalArgumentException.class,()->GroupScoreRules.finalScoreFromTotals(n("8"),n("9"),-1,n("70"),n("30")));
        assertThrows(IllegalArgumentException.class,()->GroupScoreRules.finalScoreFromTotals(n("8"),n("9"),1,n("70.001"),n("29.999")));
    }

    @Test void peerScoresRespectAssignmentMaximumAndDecimalPrecision() {
        assertEquals(n("0.01"),GroupScoreRules.score("0.01",n("20")));
        assertEquals(n("20"),GroupScoreRules.score("20",n("20")));
        for(String raw:List.of("20.01","-1","5+5","1.001","NaN",""," 5 "))
            assertThrows(IllegalArgumentException.class,()->GroupScoreRules.score(raw,n("20")),raw);
    }
}
