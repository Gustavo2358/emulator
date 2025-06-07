package core.apu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


import static org.junit.jupiter.api.Assertions.*;

// Copied from previous tests for test compilation.
// Ideally, this should be in a shared location in src/main/java.
class TestLengthCounterTableNoise { // Renamed to avoid conflict
    public static final byte[] LENGTH_TABLE = {
            10, (byte) 254, 20, 2, 40, 4, 80, 6, (byte) 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, (byte) 192, 24, 72, 26, 16, 28, 32, 30
    };
}

public class NoiseChannelTest {

    private NoiseChannel noiseChannel;
    private static final int[] NTSC_NOISE_PERIOD_TABLE_REF = { // For direct reference in tests
            4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
    };

    @BeforeEach
    void setUp() {
        noiseChannel = new NoiseChannel();
    }

    @Test
    void testWriteRegister400C_Envelope() {
        // --LC VVVV = --10 1101 (LC Halt false, Const Vol true, Env Period/Vol 13)
        noiseChannel.writeRegister(0, (byte) 0b00101101);
        assertFalse(noiseChannel.lengthCounterHalt); // Assumes direct access or getter
        assertTrue(noiseChannel.constantVolume);
        assertEquals(13, noiseChannel.envelopePeriodVolume);
        assertEquals(13, noiseChannel.currentVolume); // currentVolume should be set

        // --LC VVVV = --01 0101 (LC Halt true, Const Vol false, Env Period/Vol 5)
        noiseChannel.writeRegister(0, (byte) 0b00010101);
        assertTrue(noiseChannel.lengthCounterHalt);
        assertFalse(noiseChannel.constantVolume);
        assertEquals(5, noiseChannel.envelopePeriodVolume);
    }

    @Test
    void testWriteRegister400E_ModeAndPeriod() {
        // M--- PPPP = 1--- 0101 (Mode 1, Period Index 5)
        noiseChannel.writeRegister(2, (byte) 0b10000101);
        assertTrue(noiseChannel.modeFlag); // Assumes direct access or getter
        assertEquals(5, noiseChannel.noisePeriodIndex);
        assertEquals(NTSC_NOISE_PERIOD_TABLE_REF[5], noiseChannel.timerValue); // Check timer reloaded

        // M--- PPPP = 0--- 1111 (Mode 0, Period Index 15)
        noiseChannel.writeRegister(2, (byte) 0b00001111);
        assertFalse(noiseChannel.modeFlag);
        assertEquals(15, noiseChannel.noisePeriodIndex);
        assertEquals(NTSC_NOISE_PERIOD_TABLE_REF[15], noiseChannel.timerValue);
    }

    @Test
    void testWriteRegister400F_LengthCounterLoad() {
        noiseChannel.setEnabled(true); // Enable channel for LC loading

        // LLLL L--- = 01010 --- (LC Load index 10)
        noiseChannel.writeRegister(3, (byte) (10 << 3));
        assertEquals(10, noiseChannel.lengthCounterLoad); // Assumes direct access or getter
        assertEquals(TestLengthCounterTableNoise.LENGTH_TABLE[10], noiseChannel.lengthCounter);
    }

    @Test
    void testTimerClocking() {
        noiseChannel.writeRegister(2, (byte) 0x00); // Period index 0, period = 4
        assertEquals(NTSC_NOISE_PERIOD_TABLE_REF[0], noiseChannel.timerValue);

        int initialShiftReg = noiseChannel.shiftRegister;

        for (int i = NTSC_NOISE_PERIOD_TABLE_REF[0]; i > 0; i--) {
            noiseChannel.clock();
            assertEquals(i - 1, noiseChannel.timerValue);
            assertEquals(initialShiftReg, noiseChannel.shiftRegister); // Shift reg doesn't change until timer hits 0
        }

        noiseChannel.clock(); // Timer was 0, now reloads and clocks shift register
        assertEquals(NTSC_NOISE_PERIOD_TABLE_REF[0], noiseChannel.timerValue);
        assertNotEquals(initialShiftReg, noiseChannel.shiftRegister); // Shift register should have changed
    }

    @Test
    void testShiftRegister_Mode0() {
        noiseChannel.writeRegister(2, (byte) 0x00); // Mode 0, Period index 0 (period 4)
        noiseChannel.shiftRegister = 0b010101010101010; // Known starting state (15 bits)
        // Bit 0 is 0, Bit 1 is 1. Feedback = 0^1 = 1.
        // Expected next: 101010101010101 (shifted right, feedback in bit 14)

        // Clock timer to 0
        for(int i=0; i < NTSC_NOISE_PERIOD_TABLE_REF[0]; ++i) noiseChannel.clock();
        // Next clock will update shift register
        noiseChannel.clock();
        assertEquals(0b101010101010101, noiseChannel.shiftRegister);
    }

    @Test
    void testShiftRegister_Mode1() {
        noiseChannel.writeRegister(2, (byte) 0x80); // Mode 1, Period index 0 (period 4)
        noiseChannel.shiftRegister = 0b000000100000010; // Known state. Bit 0 is 0, Bit 6 is 1. Feedback = 0^1 = 1.
        // Expected next: 100000010000001

        // Clock timer to 0
        for(int i=0; i < NTSC_NOISE_PERIOD_TABLE_REF[0]; ++i) noiseChannel.clock();
        // Next clock will update shift register
        noiseChannel.clock();
        assertEquals(0b100000010000001, noiseChannel.shiftRegister);
    }

    @Test
    void testGetSample() {
        noiseChannel.setEnabled(true);
        noiseChannel.lengthCounter = 10; // Active
        noiseChannel.writeRegister(0, (byte) 0b00011111); // Constant volume 15
        assertEquals(15, noiseChannel.currentVolume);

        // Bit 0 of shift register is 1 -> output 0
        noiseChannel.shiftRegister = 0b010101010101011; // LSB is 1
        assertEquals(0, noiseChannel.getSample());

        // Bit 0 of shift register is 0 -> output currentVolume
        noiseChannel.shiftRegister = 0b010101010101010; // LSB is 0
        assertEquals(15, noiseChannel.getSample());

        // Length counter is 0 -> output 0
        noiseChannel.lengthCounter = 0;
        noiseChannel.shiftRegister = 0b010101010101010; // LSB is 0, but LC is 0
        assertEquals(0, noiseChannel.getSample());
    }

    @Test
    void testLengthCounterOperations() {
        noiseChannel.setEnabled(true);
        noiseChannel.writeRegister(3, (byte) (5 << 3)); // Load LC with index 5 (value 4)
        assertEquals(TestLengthCounterTableNoise.LENGTH_TABLE[5], noiseChannel.lengthCounter);

        noiseChannel.lengthCounterHalt = false;
        noiseChannel.clockLengthCounter();
        assertEquals(TestLengthCounterTableNoise.LENGTH_TABLE[5] - 1, noiseChannel.lengthCounter);

        noiseChannel.lengthCounterHalt = true;
        noiseChannel.clockLengthCounter(); // Should not decrement
        assertEquals(TestLengthCounterTableNoise.LENGTH_TABLE[5] - 1, noiseChannel.lengthCounter);
    }

    @Test
    void testSetEnabled() {
        noiseChannel.lengthCounter = 10;
        noiseChannel.setEnabled(false);
        assertEquals(0, noiseChannel.lengthCounter);
        assertFalse(noiseChannel.isEnabled); // Check internal flag too

        noiseChannel.setEnabled(true);
        assertTrue(noiseChannel.isEnabled);
        assertEquals(0, noiseChannel.lengthCounter); // Not restored by enable
    }

    @Test
    void testClockEnvelope_NoCrash() {
        assertDoesNotThrow(() -> noiseChannel.clockEnvelope());
    }
}
