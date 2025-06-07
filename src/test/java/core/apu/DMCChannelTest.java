package core.apu;

import core.apu.mocks.MockCPUBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DMCChannelTest {

    private DMCChannel dmcChannel;
    private MockCPUBus mockBus;

    private static final int[] NTSC_DMC_PERIOD_TABLE_REF = { // For direct reference in tests
            428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54
    };

    @BeforeEach
    void setUp() {
        mockBus = new MockCPUBus();
        dmcChannel = new DMCChannel(mockBus);
    }

    @Test
    void testWriteRegister4010_FlagsAndRate() {
        // IL-- RRRR = 11-- 0101 (IRQ true, Loop true, Rate index 5)
        dmcChannel.writeRegister(0, (byte) 0b11000101);
        assertTrue(dmcChannel.irqEnabled); // Assumes direct access or getter
        assertTrue(dmcChannel.loopFlag);
        assertEquals(5, dmcChannel.rateIndex);
        assertEquals(NTSC_DMC_PERIOD_TABLE_REF[5], dmcChannel.timerValue); // Check timer period update

        // IL-- RRRR = 00-- 1111 (IRQ false, Loop false, Rate index 15)
        dmcChannel.writeRegister(0, (byte) 0b00001111);
        assertFalse(dmcChannel.irqEnabled);
        assertFalse(dmcChannel.loopFlag);
        assertEquals(15, dmcChannel.rateIndex);
        assertEquals(NTSC_DMC_PERIOD_TABLE_REF[15], dmcChannel.timerValue);
        assertFalse(dmcChannel.isIRQAsserted()); // Writing with IRQ disable should clear pending IRQ
    }

    @Test
    void testWriteRegister4011_DirectLoad() {
        // DDDD DDD = 0101010 (Value 42)
        dmcChannel.writeRegister(1, (byte) 0b0101010);
        assertEquals(42, dmcChannel.directLoad); // Assumes direct access or getter
        assertEquals(42, dmcChannel.outputLevel); // Output level should be updated
    }

    @Test
    void testWriteRegister4012_SampleAddress() {
        // AAAA AAAA = 11000011 (Value 0xC3) -> Address $C000 + (0xC3 * 64)
        dmcChannel.writeRegister(2, (byte) 0xC3);
        assertEquals(0xC3, dmcChannel.sampleAddressBase); // Assumes direct access or getter
        // Actual start address checked during sample fetching tests
    }

    @Test
    void testWriteRegister4013_SampleLength() {
        // LLLL LLLL = 00000010 (Value 2) -> Length (2 * 16) + 1 = 33 bytes
        dmcChannel.writeRegister(3, (byte) 0x02);
        assertEquals(0x02, dmcChannel.sampleLength); // Assumes direct access or getter
        // Actual length calculation checked during sample fetching tests
    }

    @Test
    void testTimerClocking() {
        dmcChannel.writeRegister(0, (byte) 0x00); // Rate index 0, period 428
        assertEquals(NTSC_DMC_PERIOD_TABLE_REF[0], dmcChannel.timerValue);
        dmcChannel.setEnabled(true); // Timer only clocks if enabled

        for (int i = NTSC_DMC_PERIOD_TABLE_REF[0]; i > 0; i--) {
            dmcChannel.clock(); // This also processes output unit if timer hits 0
            // If timerValue becomes 0 within this loop due to sample processing,
            // it will be reloaded. So we check relative decrement unless it reloads.
            if (dmcChannel.timerValue != NTSC_DMC_PERIOD_TABLE_REF[0]) { // if it didn't reload
                 assertEquals(i - 1, dmcChannel.timerValue);
            }
        }
        // After exactly PERIOD clocks, it should have reloaded and ticked output unit once.
        // The next clock call will be the first tick of the new period.
        int oldTimer = dmcChannel.timerValue;
        dmcChannel.clock();
        assertEquals(oldTimer -1, dmcChannel.timerValue);
    }

    @Test
    void testSampleFetchingAndProcessing() {
        // Sample data: 0xAA (10101010), 0x55 (01010101)
        // Output starts at, say, 64.
        // Bit 0 (LSB of 0xAA is 0): 64-2 = 62
        // Bit 1 (1): 62+2 = 64
        // Bit 2 (0): 64-2 = 62
        // Bit 3 (1): 62+2 = 64
        // Bit 4 (0): 64-2 = 62
        // Bit 5 (1): 62+2 = 64
        // Bit 6 (0): 64-2 = 62
        // Bit 7 (1): 62+2 = 64
        // Next byte 0x55 (LSB 1)
        // Bit 0 (1): 64+2 = 66
        // Bit 1 (0): 66-2 = 64
        // ...

        mockBus.mockMemoryLoad(0xC000 + (0x00 * 64), new byte[]{(byte)0xAA, (byte)0x55});
        dmcChannel.writeRegister(2, (byte) 0x00); // Sample address $C000
        dmcChannel.writeRegister(3, (byte) 0x00); // Sample length (0*16)+1 = 1 byte initially. Let's make it 2.
        dmcChannel.writeRegister(3, (byte) ((2-1)/16)); // Length of 2 bytes -> (2-1)/16 = 0 -> value 0, so length is 1.
                                                       // (value * 16) + 1. So for 2 bytes, (value*16)+1 = 2 -> value*16 = 1. Not possible.
                                                       // (1*16)+1 = 17 bytes. (0*16)+1 = 1 byte.
                                                       // Let's set sampleLength directly for test clarity.
        dmcChannel.sampleLength = 2; // value for (2 bytes - 1) / 16 would be 0 with remainder.
                                    // Let's use a length that matches the calculation: (N*16)+1.
                                    // For N=0, length=1. For N=1, length=17.
        dmcChannel.sampleAddressBase = 0; // Address = $C000
        dmcChannel.sampleLength = 0;      // Length = 1 byte ($C000)
                                          // This means it will try to load 1 byte.

        dmcChannel.writeRegister(1, (byte) 64); // Initial output level 64
        dmcChannel.writeRegister(0, (byte) 0x0F); // Rate index 15 (period 54), IRQ off, Loop off
        dmcChannel.setEnabled(true); // This should call restartSample

        assertEquals(0xC000, dmcChannel.currentAddress);
        assertEquals(1, dmcChannel.bytesRemaining); // (0*16)+1

        // Simulate enough clocks to process the first bit of 0xAA (LSB is 0)
        for (int i = 0; i < NTSC_DMC_PERIOD_TABLE_REF[15]; i++) dmcChannel.clock();
        assertEquals(62, dmcChannel.outputLevel); // 64 - 2
        assertEquals(7, dmcChannel.bitsRemainingInShifter); // 1 bit processed
        assertFalse(dmcChannel.sampleBufferEmpty);

        // Process remaining 7 bits of 0xAA
        byte expectedLevel = 62;
        for(int bitIdx = 1; bitIdx < 8; bitIdx++) {
            if (((0xAA >> bitIdx) & 1) == 1) expectedLevel += 2; else expectedLevel -= 2;
            if (expectedLevel > 127) expectedLevel = 127; if (expectedLevel < 0) expectedLevel = 0; // clamp
            for (int i = 0; i < NTSC_DMC_PERIOD_TABLE_REF[15]; i++) dmcChannel.clock();
            assertEquals(expectedLevel, dmcChannel.outputLevel, "Bit " + bitIdx);
        }

        assertEquals(0, dmcChannel.bitsRemainingInShifter);
        assertTrue(dmcChannel.sampleBufferEmpty);
        assertEquals(0, dmcChannel.bytesRemaining); // First byte consumed
        assertEquals(0xC001, dmcChannel.currentAddress);
    }


    @Test
    void testLoopFlag() {
        mockBus.mockMemoryLoad(0xC000, new byte[]{(byte)0xFF}); // Sample data
        dmcChannel.writeRegister(2, (byte) 0x00); // Sample address $C000
        dmcChannel.writeRegister(3, (byte) 0x00); // Sample length 1 byte
        dmcChannel.writeRegister(0, (byte) 0b01001111); // Loop true, rate 15
        dmcChannel.writeRegister(1, (byte) 64);   // Initial output
        dmcChannel.setEnabled(true);

        assertEquals(1, dmcChannel.bytesRemaining);

        // Clock through the 8 bits of the sample byte
        for (int bit = 0; bit < 8; bit++) {
            for (int i = 0; i < NTSC_DMC_PERIOD_TABLE_REF[15]; i++) dmcChannel.clock();
        }
        assertEquals(0, dmcChannel.bytesRemaining);
        // Next clock cycle for the timer should trigger restart due to loop flag
        for (int i = 0; i < NTSC_DMC_PERIOD_TABLE_REF[15]; i++) dmcChannel.clock();

        assertEquals(1, dmcChannel.bytesRemaining, "Should have restarted due to loop");
        assertEquals(0xC000, dmcChannel.currentAddress, "Address should reset to start for loop");
    }

    @Test
    void testIRQ() {
        mockBus.mockMemoryLoad(0xC000, new byte[]{(byte)0x01});
        dmcChannel.writeRegister(2, (byte) 0x00); // Sample Addr $C000
        dmcChannel.writeRegister(3, (byte) 0x00); // Sample Length 1 byte
        dmcChannel.writeRegister(0, (byte) 0b10001111); // IRQ true, Loop false, Rate 15
        dmcChannel.setEnabled(true);

        assertFalse(dmcChannel.isIRQAsserted());

        // Clock through the sample
        for (int bit = 0; bit < 8; bit++) {
            for (int i = 0; i < NTSC_DMC_PERIOD_TABLE_REF[15]; i++) dmcChannel.clock();
        }
        assertEquals(0, dmcChannel.bytesRemaining);

        // Next relevant timer clock should trigger IRQ
        for (int i = 0; i < NTSC_DMC_PERIOD_TABLE_REF[15]; i++) dmcChannel.clock();
        assertTrue(dmcChannel.isIRQAsserted());

        dmcChannel.clearIRQ();
        assertFalse(dmcChannel.isIRQAsserted());
    }

    @Test
    void testSetEnabled() {
        dmcChannel.bytesRemaining = 10; // Has a sample playing
        dmcChannel.setEnabled(false);
        assertEquals(0, dmcChannel.bytesRemaining); // Should stop sample

        dmcChannel.writeRegister(2, (byte)0x01); // Sample Addr
        dmcChannel.writeRegister(3, (byte)0x00); // Sample Length 1 byte
        mockBus.mockMemoryLoad(0xC000 + (0x01*64), new byte[]{(byte)0xFF});

        assertEquals(0, dmcChannel.bytesRemaining);
        dmcChannel.setEnabled(true); // Enable when bytesRemaining is 0
        assertEquals(1, dmcChannel.bytesRemaining, "Should start new sample");
        assertEquals(0xC000 + (0x01*64), dmcChannel.currentAddress);
    }
}
