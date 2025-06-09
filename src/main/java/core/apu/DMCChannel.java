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
    private boolean pendingStallRequest = false; // True if DMC requests a CPU stall for memory fetch
    private boolean needsToFetchByte = false;    // True if DMC is ready to fetch a byte after CPU stall
    private int stallCyclesRemaining = 0;       // Counter for DMC stall duration

    // NTSC DMC Period Lookup Table (CPU cycles per output bit)
    private static final int[] NTSC_DMC_PERIOD_TABLE = {
            428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54
    };
    // PAL has a different table

    public DMCChannel() { // Constructor no longer takes CPUBus
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

    public void setBus(CPUBus bus) { // Added setBus method
        this.bus = bus;
    }

    public void writeRegister(int register, byte value) {
        switch (register) {
            case 0: // $4010 - IRQ, Loop, Frequency
                this.irqEnabled = (value & 0x80) != 0;
                this.loopFlag = (value & 0x40) != 0;
                this.rateIndex = value & 0x0F;
                // Timer value is reloaded in clock() when it reaches 0, using NTSC_DMC_PERIOD_TABLE[this.rateIndex]
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
        if (stallCyclesRemaining > 0) {
            stallCyclesRemaining--;
            return; // DMC is stalled, do nothing else
        }

        if (needsToFetchByte) {
            // This part is executed after APU has stalled CPU and called clearPendingStallRequestAndSetNeedsFetch()
            if (bytesRemaining > 0 && bus != null) { // Added null check for bus
                sampleBuffer = (byte) bus.read(currentAddress);
                currentAddress++; // Simple increment, relies on mapper for $C000-$FFFF range
                                  // If currentAddress wraps 0xFFFF->0x0000, it reads from new address via bus.
                bytesRemaining--;
                sampleBufferEmpty = false;
                bitsRemainingInShifter = 8;
            } else {
                // This case should ideally not be reached if logic setting needsToFetchByte is correct
                // (i.e., it only sets it if bytesRemaining > 0 or loop re-initializes)
                sampleBufferEmpty = true; // Ensure it's marked empty
            }
            needsToFetchByte = false; // Consumed the fetch request
        }

        if (!isEnabled) {
            return;
        }

        if (timerValue > 0) {
            timerValue--;
        } else {
            timerValue = NTSC_DMC_PERIOD_TABLE[this.rateIndex]; // Reload timer

            if (bitsRemainingInShifter == 0) {
                if (sampleBufferEmpty) {
                    if (bytesRemaining > 0) {
                        this.pendingStallRequest = true;
                        this.stallCyclesRemaining = 4; // Stall for 4 cycles
                        return; // Exit clock, APU will handle stall & call clearPendingStallRequestAndSetNeedsFetch
                    } else { // No bytes remaining
                        if (loopFlag) {
                            restartSample(); // Sets currentAddress and bytesRemaining
                            if (bytesRemaining > 0) { // If restartSample actually found a sample
                                this.pendingStallRequest = true;
                                this.stallCyclesRemaining = 4; // Stall for 4 cycles
                                return; // Exit for stall before fetching the first byte of looped sample
                            }
                        } else if (irqEnabled) {
                            irqPending = true;
                        }
                        // If not looping and bytesRemaining is 0 (still), no more bits to shift.
                        if (bytesRemaining == 0 && sampleBufferEmpty) return; // Nothing more to do
                    }
                }
            }

            // Shifter and output logic (only if sample buffer is not empty and has bits)
            if (!sampleBufferEmpty && bitsRemainingInShifter > 0) {
                int bitToOutput = (sampleBuffer & 0x01); // Get LSB

                if (bitToOutput == 1) { // Increase DAC output
                    if (outputLevel <= 125) { // Max is 127, increment by 2 should not exceed if <=125
                        outputLevel += 2;
                    }
                } else { // Decrease DAC output
                    if (outputLevel >= 2) { // Min is 0, decrement by 2 should not go below if >=2
                        outputLevel -= 2;
                    }
                }
                sampleBuffer >>= 1; // Consume the LSB
                bitsRemainingInShifter--;

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

    public boolean hasPendingStallRequest() {
        return pendingStallRequest;
    }

    public void clearPendingStallRequestAndSetNeedsFetch() {
        this.pendingStallRequest = false;
        this.needsToFetchByte = true;
    }

    public boolean isIRQAsserted() {
        return irqPending;
    }

    public boolean isActive() { // Added method for $4015 status
        return bytesRemaining > 0;
    }

    public void clearIRQ() {
        this.irqPending = false;
    }
}
