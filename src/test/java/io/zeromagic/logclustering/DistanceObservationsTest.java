package io.zeromagic.logclustering;

import io.zeromagic.logclustering.input.LogEntry;
import io.zeromagic.logclustering.input.Tokenizer;
import io.zeromagic.logclustering.simple.TermVector;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceObservationsTest {
    @Test
    void theseAreStrangelyFarApart() {
        // billing-usage-589df84987-j9tzz [2024-09-24T07:00:02.1360000Z] WARNING fish.payara.cloud.billing.usage.distribution.DistributionDao : Adding to DB to ignore usage of 3104ffe6-b92c-44e4-911f-f435f1188116
        var message1 = new LogEntry() {
            @Override
            public String body() {
                return "Adding to DB to ignore usage of 3104ffe6-b92c-44e4-911f-f435f1188116";
            }

            @Override
            public String exception() {
                return null;
            }

            @Override
            public Map<String, String> metadata() {
                return Map.of("Pod", "billing-usage-589df84987-j9tzz",
                        "Timestamp", "2024-09-24T07:00:02.1360000Z",
                        "Level", "WARNING");
            }
        };
        //
        // billing-usage-66c569554f-pbb8v [2024-09-12T14:40:07.3330000Z] WARNING fish.payara.cloud.billing.usage.distribution.DistributionDao : Adding to DB to ignore usage of 3104ffe6-b92c-44e4-911f-f435f1188116
        var message2 = new LogEntry() {
            @Override
            public String body() {
                return "Adding to DB to ignore usage of 3104ffe6-b92c-44e4-911f-f435f1188116";
            }

            @Override
            public String exception() {
                return null;
            }

            @Override
            public Map<String, String> metadata() {
                return Map.of("Pod", "billing-usage-66c569554f-pbb8v",
                        "Timestamp", "2024-09-12T14:40:07.3330000Z",
                        "Level", "WARNING");
            }
        };
        var tv1 = TermVector.of(message1, Tokenizer.SIMPLE);
        var tv2 = TermVector.of(message2, Tokenizer.SIMPLE);
        // this used to be almost 0.9, but that was a bug in magnitude calculation
        assertThat(tv1.cosineDistance(tv2)).isLessThan(0.4);
    }
}
