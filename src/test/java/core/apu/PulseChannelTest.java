package core.apu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

// Copied from TriangleChannel.java for test compilation.
// Ideally, this should be in a shared location in src/main/java.
class TestLengthCounterTable {
    public static final byte[] LENGTH_TABLE = {
            10, (byte) 254, 20, 2, 40, 4, 80, 6, (byte) 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, (byte) 192, 24, 72, 26, 16, 28, 32, 30
    };
}

public class PulseChannelTest {

    private PulseChannel pulseChannel;

    @BeforeEach
    void setUp() {
        pulseChannel = new PulseChannel();
    }

    @Test
    void testWriteRegister0_VolEnvelope() {
        // DDLC VVVV = 0110 1111 (Duty 25%, LC Halt false, Const Vol true, Env Period/Vol 15)
        pulseChannel.writeRegister(0, (byte) 0b01101111);
        assertEquals(1, pulseChannel.dutyCycle); // Internal field, assume accessible or getter
        assertFalse(pulseChannel.lengthCounterHalt);
        assertTrue(pulseChannel.constantVolume);
        assertEquals(15, pulseChannel.envelopePeriodVolume);
        assertEquals(15, pulseChannel.currentVolume); // currentVolume should be set

        // DDLC VVVV = 1001 0101 (Duty 50%, LC Halt true, Const Vol false, Env Period/Vol 5)
        pulseChannel.writeRegister(0, (byte) 0b10010101);
        assertEquals(2, pulseChannel.dutyCycle);
        assertTrue(pulseChannel.lengthCounterHalt);
        assertFalse(pulseChannel.constantVolume);
        assertEquals(5, pulseChannel.envelopePeriodVolume);
        // currentVolume might be reset by envelope logic, or keep old if const vol false
        // For now, assume it's not changed directly if constantVolume is false by this write
    }

    @Test
    void testWriteRegister1_Sweep() {
        // EPPP NSSS = 1010 1011 (Sweep on, Period 2, Negate true, Shift 3)
        pulseChannel.writeRegister(1, (byte) 0b10101011);
        assertTrue(pulseChannel.sweepEnabled);
        assertEquals(2, pulseChannel.sweepPeriod);
        assertTrue(pulseChannel.sweepNegate);
        assertEquals(3, pulseChannel.sweepShift);
    }

    @Test
    void testWriteRegister2_TimerLow() {
        pulseChannel.writeRegister(2, (byte) 0xAB);
        assertEquals(0xAB, pulseChannel.timerLow);
        // Check combined timer period
        pulseChannel.writeRegister(3, (byte) 0b00000001); // Timer high 1
        assertEquals(0x1AB, pulseChannel.timerValue); // (0x01 << 8) | 0xAB
    }

    @Test
    void testWriteRegister3_TimerHighLengthCounter() {
        pulseChannel.setEnabled(true); // Enable to allow LC load
        // LLLL LTTT = 01010 011 (LC Load 10, Timer High 3)
        pulseChannel.writeRegister(3, (byte) 0b01010011);
        assertEquals(3, pulseChannel.timerHigh);
        assertEquals(10, pulseChannel.lengthCounterLoad); // Index 10
        assertEquals(TestLengthCounterTable.LENGTH_TABLE[10], pulseChannel.lengthCounter);

        // Check combined timer period
        pulseChannel.writeRegister(2, (byte) 0xCD); // Timer low
        assertEquals((3 << 8) | 0xCD, pulseChannel.timerValue);
        assertEquals(0, pulseChannel.dutySequencePosition); // Writing to $4003 resets sequencer
    }

    @Test
    void testTimerClocking() {
        pulseChannel.writeRegister(2, (byte) 3); // Timer period 3 (low bits)
        pulseChannel.writeRegister(3, (byte) 0); // Timer period 3 (high bits 0)
        // timerValue (period) is 3. timerCounter starts at this value.

        assertEquals(3, pulseChannel.timerValue); // Period
        pulseChannel.timerCounter = 3; // Initialize countdown explicitly for test clarity

        pulseChannel.clock(); // counter = 2
        assertEquals(2, pulseChannel.timerCounter);
        pulseChannel.clock(); // counter = 1
        assertEquals(1, pulseChannel.timerCounter);
        pulseChannel.clock(); // counter = 0
        assertEquals(0, pulseChannel.timerCounter);
        int oldDutyPos = pulseChannel.dutySequencePosition;
        pulseChannel.clock(); // counter = 3 (reloaded), duty sequence ++
        assertEquals(3, pulseChannel.timerCounter);
        assertEquals((oldDutyPos + 1) % 8, pulseChannel.dutySequencePosition);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 12_5", // Duty 0 (12.5%) -> sequence {0,1,0,0,0,0,0,0}
            "1, 25",   // Duty 1 (25%)   -> sequence {0,1,1,0,0,0,0,0}
            "2, 50",   // Duty 2 (50%)   -> sequence {0,1,1,1,1,0,0,0}
            "3, 25N"   // Duty 3 (25%N)  -> sequence {1,0,0,1,1,1,1,1} (inverted for output)
    })
    void testDutyCycleSequence(int dutySetting, String description) {
        pulseChannel.writeRegister(0, (byte) ((dutySetting << 6) | 0b00011111)); // Const vol 15
        pulseChannel.setEnabled(true);
        pulseChannel.lengthCounter = 10; // Keep channel active

        byte[][] DUTY_SEQUENCES = {
                {0, 1, 0, 0, 0, 0, 0, 0},
                {0, 1, 1, 0, 0, 0, 0, 0},
                {0, 1, 1, 1, 1, 0, 0, 0},
                {1, 0, 0, 1, 1, 1, 1, 1}
        };

        // Set timer period to 0 so each clock() advances duty step
        pulseChannel.writeRegister(2, (byte) 0);
        pulseChannel.writeRegister(3, (byte) 0);
        pulseChannel.timerCounter = 0; // Ensure it reloads and steps immediately

        for (int i = 0; i < 8; i++) {
            assertEquals(i, pulseChannel.dutySequencePosition, "Duty position before clock " + i);
            byte expectedOutput = (DUTY_SEQUENCES[dutySetting][i] == 1) ? (byte) 15 : (byte) 0;
            assertEquals(expectedOutput, pulseChannel.getSample(),
                    "Sample for duty " + dutySetting + " at step " + i);
            pulseChannel.clock(); // Advances dutySequencePosition
        }
    }

    @Test
    void testSetEnabled() {
        pulseChannel.lengthCounter = 50; // Arbitrary non-zero value
        assertTrue(pulseChannel.isLengthCounterActive());

        pulseChannel.setEnabled(false);
        assertFalse(pulseChannel.isEnabled); // Check internal flag
        assertEquals(0, pulseChannel.lengthCounter);
        assertFalse(pulseChannel.isLengthCounterActive());

        pulseChannel.setEnabled(true);
        assertTrue(pulseChannel.isEnabled);
        assertEquals(0, pulseChannel.lengthCounter); // Length counter not restored by enable
        assertFalse(pulseChannel.isLengthCounterActive());
    }

    @Test
    void testLengthCounterLoadAndClock() {
        pulseChannel.setEnabled(true);
        // LLLL LTTT = 00101 000 (LC load index 5, timer high 0)
        // LengthCounterTable[5] = 4
        pulseChannel.writeRegister(3, (byte) (5 << 3));
        assertEquals(TestLengthCounterTable.LENGTH_TABLE[5], pulseChannel.lengthCounter);
        assertTrue(pulseChannel.isLengthCounterActive());

        pulseChannel.clockLengthCounter(); // Simulate frame counter clock
        assertEquals(TestLengthCounterTable.LENGTH_TABLE[5] - 1, pulseChannel.lengthCounter);

        pulseChannel.lengthCounterHalt = true;
        pulseChannel.clockLengthCounter(); // Should not decrement
        assertEquals(TestLengthCounterTable.LENGTH_TABLE[5] - 1, pulseChannel.lengthCounter);

        pulseChannel.lengthCounterHalt = false;
        pulseChannel.lengthCounter = 1;
        pulseChannel.clockLengthCounter(); // Decrement to 0
        assertEquals(0, pulseChannel.lengthCounter);
        assertFalse(pulseChannel.isLengthCounterActive());

        pulseChannel.clockLengthCounter(); // Stays 0
        assertEquals(0, pulseChannel.lengthCounter);
    }

    // Basic placeholder tests for envelope and sweep clock methods
    @Test
    void testClockEnvelope_NoCrash() {
        assertDoesNotThrow(() -> pulseChannel.clockEnvelope());
    }

    @Test
    void testClockSweep_NoCrash() {
        assertDoesNotThrow(() -> pulseChannel.clockSweep());
    }
}
