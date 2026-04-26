package com.example.email.load;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedPickerTest {

    @Test
    void weightedPicker_respectsRoughWeights() {
        Map<String, Integer> mix = new LinkedHashMap<>();
        mix.put("A", 80);
        mix.put("B", 20);
        ScenarioRunner.WeightedPicker picker = new ScenarioRunner.WeightedPicker(mix);

        Random rnd = new Random(42L);
        int aCount = 0;
        int total = 10_000;
        for (int i = 0; i < total; i++) {
            if (picker.next(rnd).equals("A")) aCount++;
        }
        double ratio = aCount / (double) total;
        assertThat(ratio).isBetween(0.75, 0.85);
    }
}
