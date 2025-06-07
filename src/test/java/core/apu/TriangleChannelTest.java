package core.apu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


import static org.junit.jupiter.api.Assertions.*;

// Copied from TriangleChannel.java (or previous test) for test compilation.
// Ideally, this should be in a shared location in src/main/java.
class TestLengthCounterTableTriangle { // Renamed to avoid conflict if run in same suite context
    public static final byte[] LENGTH_TABLE = {
            10, (byte) 254, 20, 2, 40, 4, 80, 6, (byte) 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, (byte) 192, 24, 72, 26, 16, 28, 32, 30
    };
}


public class TriangleChannelTest {

    private TriangleChannel triangleChannel;

    @BeforeEach
    void setUp() {
        triangleChannel = new TriangleChannel();
    }

    @Test
    void testWriteRegister4008_ControlAndLinearReload() {
        // CRRR RRRR = 1100 0101 (Control true/LC Halt, Linear Reload 69)
        triangleChannel.writeRegister(0, (byte) 0b11000101);
        assertTrue(triangleChannel.controlFlag); // Assumes direct access or getter
        assertEquals(0b1000101, triangleChannel.linearCounterReloadValue);

        // CRRR RRRR = 0010 1010 (Control false/LC Control, Linear Reload 42)
        triangleChannel.writeRegister(0, (byte) 0b00101010);
        assertFalse(triangleChannel.controlFlag);
        assertEquals(0b0101010, triangleChannel.linearCounterReloadValue);
    }

    @Test
    void testWriteRegister400A_TimerLow() {
        triangleChannel.writeRegister(2, (byte) 0xAB);
        assertEquals(0xAB, triangleChannel.timerLow); // Assumes direct access or getter
        // Verify combined period if timerHigh is set
        triangleChannel.timerHigh = 0x01; // Manually set for test
        assertEquals(((1 << 8) | 0xAB) + 1, triangleChannel.getTimerPeriod());
    }

    @Test
    void testWriteRegister400B_TimerHighLengthCounter() {
        triangleChannel.setEnabled(true); // So length counter loads
        // LLLL LTTT = 01010 011 (LC Load index 10, Timer High 3)
        triangleChannel.writeRegister(3, (byte) 0b01010011);
        assertEquals(3, triangleChannel.timerHigh); // Assumes direct access or getter
        assertEquals(10, triangleChannel.lengthCounterLoad); // Assumes direct access or getter
        assertTrue(triangleChannel.linearCounterReloadFlag); // Assumes direct access or getter

        // Check if length counter is loaded (assuming controlFlag is false by default or set appropriately)
        triangleChannel.controlFlag = false; // Ensure LC is not halted for loading
        triangleChannel.writeRegister(3, (byte) 0b01010011); // Re-write to trigger load with correct controlFlag
        assertEquals(TestLengthCounterTableTriangle.LENGTH_TABLE[10], triangleChannel.lengthCounter);

        // Verify combined period
        triangleChannel.timerLow = 0xCD; // Manually set for test
        assertEquals(((3 << 8) | 0xCD) + 1, triangleChannel.getTimerPeriod());
    }

    @Test
    void testTimerClockingAndSequence() {
        // Setup for sequencing: LC and Linear Counter must be > 0
        triangleChannel.lengthCounter = 10;
        triangleChannel.linearCounter = 5;
        triangleChannel.timerLow = 0; // Period of 1 (0+1)
        triangleChannel.timerHigh = 0;
        triangleChannel.timerValue = triangleChannel.getTimerPeriod(); // Load initial countdown

        for (int i = 0; i < 32; i++) {
            assertEquals(i, triangleChannel.sequencePosition, "Sequence position before step " + i);
            // Timer countdown to 0
            triangleChannel.clock(); // timerValue becomes 0
            assertEquals(0, triangleChannel.timerValue);
            // Next clock reloads timer and advances sequence
            triangleChannel.clock(); // timerValue reloaded, sequencePosition increments
            assertEquals(triangleChannel.getTimerPeriod(), triangleChannel.timerValue);
        }
        assertEquals(0, triangleChannel.sequencePosition, "Sequence should wrap around");
    }

    @Test
    void testTriangleSequenceOutput() {
        triangleChannel.lengthCounter = 10; // Keep active
        triangleChannel.linearCounter = 5;  // Keep active
        triangleChannel.timerLow = 0;     // Period 1 for quick stepping
        triangleChannel.timerHigh = 0;
        triangleChannel.timerValue = triangleChannel.getTimerPeriod();


        byte[] TRIANGLE_SEQUENCE = {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
        };

        for (int i = 0; i < 32; i++) {
            assertEquals(TRIANGLE_SEQUENCE[i], triangleChannel.getSample(), "Sample at sequence step " + i);
            triangleChannel.clock(); // timer value = 0
            triangleChannel.clock(); // timer reloaded, sequence step
        }
    }


    @Test
    void testLinearCounter() {
        triangleChannel.writeRegister(0, (byte) 0b01000001); // Control false (active), Reload value 1
        assertEquals(1, triangleChannel.linearCounterReloadValue);
        assertFalse(triangleChannel.controlFlag);

        // Set reload flag by writing to $400B
        triangleChannel.writeRegister(3, (byte) 0x00); // Any write to $400B sets the flag
        assertTrue(triangleChannel.linearCounterReloadFlag);

        triangleChannel.clockLinearCounter(); // Should reload
        assertEquals(1, triangleChannel.linearCounter);
        assertFalse(triangleChannel.linearCounterReloadFlag); // Flag should be cleared

        triangleChannel.clockLinearCounter(); // Should decrement
        assertEquals(0, triangleChannel.linearCounter);

        triangleChannel.clockLinearCounter(); // Stays 0
        assertEquals(0, triangleChannel.linearCounter);

        // Test halt
        triangleChannel.writeRegister(0, (byte) 0b11000010); // Control true (halt), Reload value 2
        triangleChannel.writeRegister(3, (byte) 0x00); // Set reload flag
        triangleChannel.clockLinearCounter(); // Reloads to 2
        assertEquals(2, triangleChannel.linearCounter);
        assertTrue(triangleChannel.linearCounterReloadFlag); // Flag NOT cleared due to controlFlag

        triangleChannel.clockLinearCounter(); // Should NOT decrement because controlFlag is true (halt)
        assertEquals(2, triangleChannel.linearCounter);
    }

    @Test
    void testLengthCounter() {
        triangleChannel.setEnabled(true);
        triangleChannel.controlFlag = false; // LC not halted

        // LLLL LTTT = 00101 000 (LC load index 5 -> value 4)
        triangleChannel.writeRegister(3, (byte) (5 << 3));
        assertEquals(TestLengthCounterTableTriangle.LENGTH_TABLE[5], triangleChannel.lengthCounter);

        triangleChannel.clockLengthCounter();
        assertEquals(TestLengthCounterTableTriangle.LENGTH_TABLE[5] - 1, triangleChannel.lengthCounter);

        // Halt
        triangleChannel.controlFlag = true;
        triangleChannel.clockLengthCounter(); // Should not decrement
        assertEquals(TestLengthCounterTableTriangle.LENGTH_TABLE[5] - 1, triangleChannel.lengthCounter);
        triangleChannel.controlFlag = false; // Un-halt

        triangleChannel.lengthCounter = 1;
        triangleChannel.clockLengthCounter();
        assertEquals(0, triangleChannel.lengthCounter);
        triangleChannel.clockLengthCounter(); // Stays 0
        assertEquals(0, triangleChannel.lengthCounter);
    }

    @Test
    void testSetEnabled() {
        triangleChannel.lengthCounter = 10;
        triangleChannel.setEnabled(false);
        assertEquals(0, triangleChannel.lengthCounter);

        triangleChannel.setEnabled(true); // Does not restore length counter
        assertEquals(0, triangleChannel.lengthCounter);
    }

    @Test
    void testGetSampleMutingConditions() {
        // Valid state first
        triangleChannel.timerLow = 2; triangleChannel.timerHigh = 0; // Period = 3
        triangleChannel.timerValue = triangleChannel.getTimerPeriod();
        triangleChannel.lengthCounter = 1;
        triangleChannel.linearCounter = 1;
        triangleChannel.sequencePosition = 5; // Expected output 5
        assertNotEquals(0, triangleChannel.getSample());
        assertEquals(5, triangleChannel.getSample());


        // 1. Timer period < 2 (effectively timer value of 0 or 1 for T+1 period)
        triangleChannel.timerLow = 0; triangleChannel.timerHigh = 0; // Period = 1 (<2)
        triangleChannel.timerValue = triangleChannel.getTimerPeriod();
        assertEquals(0, triangleChannel.getSample(), "Muted if timer period < 2");

        triangleChannel.timerLow = 1; triangleChannel.timerHigh = 0; // Period = 2 (>=2)
        triangleChannel.timerValue = triangleChannel.getTimerPeriod();
        assertNotEquals(0, triangleChannel.getSample(), "Not muted if timer period == 2");


        // Reset to valid state
        triangleChannel.timerLow = 2; triangleChannel.timerHigh = 0; // Period = 3
        triangleChannel.timerValue = triangleChannel.getTimerPeriod();
        triangleChannel.lengthCounter = 1;
        triangleChannel.linearCounter = 1;
        assertNotEquals(0, triangleChannel.getSample());

        // 2. Length counter is 0
        triangleChannel.lengthCounter = 0;
        assertEquals(0, triangleChannel.getSample(), "Muted if length counter is 0");
        triangleChannel.lengthCounter = 1; // Restore

        // 3. Linear counter is 0
        triangleChannel.linearCounter = 0;
        assertEquals(0, triangleChannel.getSample(), "Muted if linear counter is 0");
        triangleChannel.linearCounter = 1; // Restore

        assertNotEquals(0, triangleChannel.getSample(), "Sample should be non-zero after restoring LC and LinearC");
    }
}
