package com.harmonia.ui.widget;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Reference values: Reference Guide, MIXENV page ("Mix Envelope Points" figure). */
class VectorPadTest {

    @Test
    void centreIsAnEqualMix() {
        assertArrayEquals(new int[] { 25, 25, 25, 25 }, VectorPad.mixPercentages(0, 0));
    }

    @Test
    void cornersOfTheDiamondAreOneWave() {
        assertArrayEquals(new int[] { 100, 0, 0, 0 }, VectorPad.mixPercentages(-127, 0)); // A, left
        assertArrayEquals(new int[] { 0, 100, 0, 0 }, VectorPad.mixPercentages(0, 127));  // B, top
        assertArrayEquals(new int[] { 0, 0, 100, 0 }, VectorPad.mixPercentages(127, 0));  // C, right
        assertArrayEquals(new int[] { 0, 0, 0, 100 }, VectorPad.mixPercentages(0, -127)); // D, bottom
    }

    @Test
    void cornersOfTheSquareAreClippedToTheMiddleOfAnEdge() {
        assertArrayEquals(new int[] { 50, 50, 0, 0 }, VectorPad.mixPercentages(-127, 127));
        assertArrayEquals(new int[] { 0, 50, 50, 0 }, VectorPad.mixPercentages(127, 127));
        assertArrayEquals(new int[] { 0, 0, 50, 50 }, VectorPad.mixPercentages(127, -127));
        assertArrayEquals(new int[] { 50, 0, 0, 50 }, VectorPad.mixPercentages(-127, -127));
    }

    @Test
    void percentagesAlwaysSumTo100() {
        for (int ac = -127; ac <= 127; ac += 7) {
            for (int bd = -127; bd <= 127; bd += 11) {
                int[] p = VectorPad.mixPercentages(ac, bd);
                assertEquals(100, p[0] + p[1] + p[2] + p[3], "ac=" + ac + " bd=" + bd);
            }
        }
    }

    @Test
    void clipKeepsPointsInsideAndMovesOutsidePointsToTheEdge() {
        assertArrayEquals(new int[] { 30, -40 }, VectorPad.clipToDiamond(30, -40));
        int[] clipped = VectorPad.clipToDiamond(127, 127);
        assertArrayEquals(new int[] { 64, 64 }, clipped); // 63.5 rounded
    }

    @Test
    void twoWavesMixIsLinearOnTheAcAxis() {
        assertEquals(100, VectorPad.mixTwoWaves(-127));
        assertEquals(50, VectorPad.mixTwoWaves(0));
        assertEquals(0, VectorPad.mixTwoWaves(127));
    }

    @Test
    void movingAPointNotifiesListenersWithClippedValues() {
        VectorPad pad = new VectorPad();
        AtomicInteger lastAc = new AtomicInteger();
        pad.addListener(new VectorPad.Listener() {
            @Override
            public void pointChanged(int point, int ac, int bd) {
                lastAc.set(ac);
            }

            @Override
            public void pointSelected(int point) {
            }
        });
        pad.setPoint(2, 200, 0);
        assertEquals(127, lastAc.get());
        assertEquals(127, pad.getAc(2));
    }

    @Test
    void twoWavePatchIgnoresTheBdAxis() {
        VectorPad pad = new VectorPad();
        pad.setWaveCount(2);
        pad.setPoint(1, -20, 90);
        assertEquals(0, pad.getBd(1));
    }
}
