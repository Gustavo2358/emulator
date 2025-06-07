package core.apu;

import core.CPUBus; // Needs reference to Bus for memory access

public class DMCChannel {
    // Registers
    private boolean irqEnabled;         // $4010 bit 7
    private boolean loopFlag;           // $4010 bit 6
    private int rateIndex;              // $4010 bits 0-3 (index into period table)

    private int directLoad;             // $4011 bits 0-6 (7-bit DAC direct load)

    private int sampleAddressBase;      // $4012 bits 0-7 (Sample address = %11AAAAAA.AA000000 = $C000 + (value * 64))
    private int sampleLength;           // $4013 bits 0-7 (Sample length = %LLLLLLLL.00000001 = (value * 16) + 1 bytes)

    // Internal state
    private CPUBus bus;
    private boolean isEnabled;

    private int timerValue;             // Counts down from period table value
    private int currentAddress;
    private int bytesRemaining;

    private byte sampleBuffer;          // Holds the current byte being shifted out
    private boolean sampleBufferEmpty;
    private int bitsRemainingInShifter; // Counts 8 bits for each byte
    private byte outputLevel;           // 7-bit DAC output level (0-127)

    private boolean irqPending;

    // NTSC DMC Period Lookup Table (CPU cycles per output bit)
    private static final int[] NTSC_DMC_PERIOD_TABLE = {
            428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54
    };
    // PAL has a different table

    public DMCChannel(CPUBus bus) {
        this.bus = bus;
        this.irqEnabled = false;
        this.loopFlag = false;
        this.rateIndex = 0;
        this.directLoad = 0;
        this.sampleAddressBase = 0;
        this.sampleLength = 0;
        this.outputLevel = 0; // Should be initialized by $4011 write
        this.sampleBufferEmpty = true;
        this.bitsRemainingInShifter = 0;
        this.bytesRemaining = 0;
        this.timerValue = NTSC_DMC_PERIOD_TABLE[0];
        this.irqPending = false;
    }

    public void writeRegister(int register, byte value) {
        switch (register) {
            case 0: // $4010 - IRQ, Loop, Frequency
                this.irqEnabled = (value & 0x80) != 0;
                this.loopFlag = (value & 0x40) != 0;
                this.rateIndex = value & 0x0F;
                this.timerValue = NTSC_DMC_PERIOD_TABLE[this.rateIndex];
                if (!this.irqEnabled) {
                    this.irqPending = false;
                }
                break;
            case 1: // $4011 - Direct Load (DAC)
                this.directLoad = value & 0x7F; // 7 bits
                this.outputLevel = (byte) this.directLoad; // Update output level directly
                break;
            case 2: // $4012 - Sample Address
                this.sampleAddressBase = value & 0xFF;
                // Actual address: $C000 + (value * 64)
                break;
            case 3: // $4013 - Sample Length
                this.sampleLength = value & 0xFF;
                // Actual length: (value * 16) + 1
                // This is where a new sample might be started if channel is active
                // restartSample(); // Handled by setEnabled or when bytesRemaining becomes 0
                break;
        }
    }

    private void restartSample() {
        if (this.sampleLength > 0) {
            this.currentAddress = 0xC000 + (this.sampleAddressBase * 64);
            this.bytesRemaining = (this.sampleLength * 16) + 1;
            this.sampleBufferEmpty = true; // Need to fetch the first byte
        } else {
            this.bytesRemaining = 0; // No sample to play
        }
    }

    public void clock() {
        if (!isEnabled) {
            return;
        }

        if (timerValue > 0) {
            timerValue--;
        } else {
            timerValue = NTSC_DMC_PERIOD_TABLE[this.rateIndex]; // Reload timer

            if (bitsRemainingInShifter == 0) { // Current byte processed, need new one or new bit from current
                if (sampleBufferEmpty) {
                    if (bytesRemaining > 0) {
                        // TODO: CPU STALL for 1-4 cycles if memory access is slow
                        // For now, assume instant fetch
                        sampleBuffer = (byte) bus.read(currentAddress);
                        currentAddress = (currentAddress + 1);
                        if (currentAddress == 0) currentAddress = 0x8000; // Address wrap around $FFFF -> $8000 for some mappers? Usually $C000-$FFFF is ROM.
                                                                        // For DMC, it's typically $C000-$FFFF. Wrap is not standard.
                                                                        // If currentAddress goes beyond $FFFF, it should wrap to $8000 for open bus reads on some systems,
                                                                        // but for DMC, it might just stop or read garbage.
                                                                        // Let's assume no wrap for now for simplicity or wrap within its range if specified.
                        bytesRemaining--;
                        sampleBufferEmpty = false;
                        bitsRemainingInShifter = 8;
                    } else { // No bytes remaining
                        if (loopFlag) {
                            restartSample();
                            // Need to fetch the first byte for the looped sample
                            if (bytesRemaining > 0) { // If restartSample actually found a sample
                                // TODO: CPU STALL
                                sampleBuffer = (byte) bus.read(currentAddress);
                                currentAddress = (currentAddress + 1);
                                bytesRemaining--; // Byte is consumed for the buffer
                                sampleBufferEmpty = false;
                                bitsRemainingInShifter = 8;
                            }
                        } else if (irqEnabled) {
                            irqPending = true;
                        }
                        // If no loop and no IRQ, or if IRQ already pending, channel remains silent until next $4015 write or $4013 write.
                        // If not looping and bytesRemaining is 0, no more bits to shift.
                        if (bytesRemaining == 0) return; // Nothing more to do this clock cycle if no data
                    }
                }
            }


            if (bitsRemainingInShifter > 0) { // If there's a bit to process
                int bit = sampleBuffer & 1; // Get LSB
                sampleBuffer >>= 1;
                bitsRemainingInShifter--;

                if (bit == 1) { // Increase DAC output
                    if (outputLevel <= 125) {
                        outputLevel += 2;
                    }
                } else { // Decrease DAC output
                    if (outputLevel >= 2) {
                        outputLevel -= 2;
                    }
                }
                if (bitsRemainingInShifter == 0) {
                    sampleBufferEmpty = true; // Mark buffer as empty to fetch next byte on next appropriate clock
                }
            }
        }
    }

    public byte getSample() {
        return outputLevel;
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            bytesRemaining = 0; // Stop current sample
        } else {
            // If DMC is enabled and bytesRemaining is 0, a new sample should be started.
            if (bytesRemaining == 0) {
                restartSample();
            }
        }
        if (!irqEnabled) { // Clearing IRQ if disabled by $4015 write.
            irqPending = false;
        }
    }

    public boolean isIRQAsserted() {
        return irqPending;
    }

    public void clearIRQ() {
        this.irqPending = false;
    }
}
