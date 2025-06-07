package core.apu;

public class NoiseChannel {
    // Registers
    private boolean lengthCounterHalt;  // Envelope loop ($400C bit 5)
    private boolean constantVolume;     // Envelope enable ($400C bit 4)
    private int envelopePeriodVolume;   // Envelope period or direct volume ($400C bits 0-3)

    private boolean modeFlag;           // Noise mode ($400E bit 7)
    private int noisePeriodIndex;       // Index into NTSC_NOISE_PERIOD_TABLE ($400E bits 0-3)

    private int lengthCounterLoad;      // ($400F bits 3-7)

    // Internal state
    private int timerValue;             // Current value of the timer (counts down from period table value)
    private int shiftRegister;          // 15-bit Linear Feedback Shift Register (LFSR)
    private int currentVolume;          // Current volume (set by envelope or constantVolume)
    private int lengthCounter;

    // NTSC Noise Period Lookup Table
    // These are the timer periods for the noise channel on NTSC systems.
    private static final int[] NTSC_NOISE_PERIOD_TABLE = {
            4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
    };
    // PAL has a different table.

    public NoiseChannel() {
        this.lengthCounterHalt = false;
        this.constantVolume = false;
        this.envelopePeriodVolume = 0;
        this.modeFlag = false;
        this.noisePeriodIndex = 0;
        this.lengthCounterLoad = 0;

        this.shiftRegister = 1; // Shift register is initialized to 1
        this.timerValue = NTSC_NOISE_PERIOD_TABLE[0]; // Initial timer period
        this.currentVolume = 0;
        this.lengthCounter = 0;
    }

    public void writeRegister(int register, byte value) {
        switch (register) {
            case 0: // $400C - Envelope Control
                this.lengthCounterHalt = (value & 0x20) != 0;
                this.constantVolume = (value & 0x10) != 0;
                this.envelopePeriodVolume = value & 0x0F;
                if (this.constantVolume) {
                    this.currentVolume = this.envelopePeriodVolume;
                } else {
                    // TODO: Reset envelope
                }
                break;
            case 1: // $400D - Unused
                break;
            case 2: // $400E - Mode and Period
                this.modeFlag = (value & 0x80) != 0;
                this.noisePeriodIndex = value & 0x0F;
                this.timerValue = NTSC_NOISE_PERIOD_TABLE[this.noisePeriodIndex]; // Update timer immediately
                break;
            case 3: // $400F - Length Counter Load
                this.lengthCounterLoad = (value >> 3) & 0x1F;
                if (isEnabled()) { // Only load if channel is enabled (via $4015 status register)
                     this.lengthCounter = LengthCounterTable.LENGTH_TABLE[this.lengthCounterLoad];
                }
                // TODO: Envelope is reset
                break;
        }
    }

    public void clock() {
        if (timerValue > 0) {
            timerValue--;
        } else {
            timerValue = NTSC_NOISE_PERIOD_TABLE[this.noisePeriodIndex]; // Reload timer

            // Clock the shift register
            int bit0 = shiftRegister & 1;
            int bitToXor;
            if (modeFlag) { // Mode 1 (bit 6)
                bitToXor = (shiftRegister >> 6) & 1;
            } else { // Mode 0 (bit 1)
                bitToXor = (shiftRegister >> 1) & 1;
            }
            int feedback = bit0 ^ bitToXor;
            shiftRegister >>= 1;
            shiftRegister |= (feedback << 14); // Set bit 14
        }
        // Note: Length Counter and Envelope are clocked by Frame Counter
    }

    public byte getSample() {
        // Output is 0 if:
        // - bit 0 of shift register is 1
        // - length counter is 0
        if ((shiftRegister & 1) == 1 || lengthCounter == 0) {
            return 0;
        }
        // Otherwise, output is current envelope volume
        return (byte) (currentVolume & 0x0F);
    }

    // Called by Frame Counter
    public void clockLengthCounter() {
        if (!lengthCounterHalt && lengthCounter > 0) {
            lengthCounter--;
        }
    }

    // Called by Frame Counter
    public void clockEnvelope() {
        // TODO: Implement envelope logic
        // If not constant volume mode:
        // Clock envelope divider. If divider reaches 0, clock envelope value.
        // Update currentVolume based on envelope decay.
        if (constantVolume) {
            this.currentVolume = this.envelopePeriodVolume;
        } else {
            // Actual envelope logic will go here
        }
    }

    private boolean isEnabled = false; // Internal enabled state, controlled by $4015
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            this.lengthCounter = 0;
        }
        // Enabling does not immediately reload length counter.
    }

    public boolean isLengthCounterActive() {
        return lengthCounter > 0;
    }
}
