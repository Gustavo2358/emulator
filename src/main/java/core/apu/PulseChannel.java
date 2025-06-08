package core.apu;

public class PulseChannel {
    // Registers
    private int dutyCycle;              // 2 bits (0-3) -> $4000 SQ1_VOL / $4004 SQ2_VOL [D]
    private boolean lengthCounterHalt;  // 1 bit (envelope loop) -> $4000 SQ1_VOL / $4004 SQ2_VOL [L]
    private boolean constantVolume;     // 1 bit (envelope enable) -> $4000 SQ1_VOL / $4004 SQ2_VOL [V]
    private int envelopePeriodVolume;   // 4 bits (period for envelope or constant volume) -> $4000 SQ1_VOL / $4004 SQ2_VOL [N]

    // Sweep unit registers
    private boolean sweepEnabledReg;    // $4001 bit 7 (Master enable)
    private int sweepPeriodReg;         // $4001 bits 4-6 (P)
    private boolean sweepNegateReg;     // $4001 bit 3 (N)
    private int sweepShiftReg;          // $4001 bits 0-2 (S)

    // Sweep unit internal state
    private boolean sweepReloadFlag;    // Set by $4001 write
    private int sweepDividerCounter;    // Counts down from sweepPeriodReg + 1
    private boolean sweepMuting;        // True if sweep is muting the channel
    private boolean isPulse1;           // Flag to distinguish between Pulse 1 and Pulse 2 for sweep negate quirk

    private int timerLow;               // 8 bits -> $4002 SQ1_LO / $4006 SQ2_LO
    private int timerHigh;              // 3 bits -> $4003 SQ1_HI / $4007 SQ2_HI [T high]
    private int lengthCounterLoad;      // 5 bits -> $4003 SQ1_HI / $4007 SQ2_HI [L]

    // Internal state
    private int timerValue;             // This is the 11-bit PERIOD value (T) from registers $4002/3 or $4006/7
    private int timerCounter;           // Current countdown value for the timer, counts T+1 of its own clock ticks
    private int dutySequencePosition;   // Current position in the duty cycle sequence (0-7)
    private int currentVolume;          // Current volume (considering envelope)
    private int lengthCounter;          // Length counter
    private boolean isEnabled;          // Channel enabled state (via $4015)

    // Envelope unit state
    private boolean envelopeStartFlag;  // Set when $4000/4 is written, to reset envelope
    private int envelopeDecayLevel;     // Current decay level (0-15)
    private int envelopeDividerCounter; // Counts down from envelopePeriodVolume + 1

    // Duty cycle sequences (8 steps per sequence)
    // 0: 01000000 (12.5%)
    // 1: 01100000 (25%)
    // 2: 01111000 (50%)
    // 3: 10011111 (25% negated)
    private static final byte[][] DUTY_SEQUENCES = {
            {0, 1, 0, 0, 0, 0, 0, 0},
            {0, 1, 1, 0, 0, 0, 0, 0},
            {0, 1, 1, 1, 1, 0, 0, 0},
            {1, 0, 0, 1, 1, 1, 1, 1} // Inverted compared to some docs for direct output
    };

    public PulseChannel() {
        this.dutyCycle = 0;
        this.lengthCounterHalt = false;
        this.constantVolume = false;
        this.envelopePeriodVolume = 0;
        this.sweepEnabledReg = false;
        this.sweepPeriodReg = 0;
        this.sweepNegateReg = false;
        this.sweepShiftReg = 0;
        this.timerLow = 0;
        this.timerHigh = 0;
        this.lengthCounterLoad = 0;
        this.timerValue = 0; // This stores the period T from registers
        this.timerCounter = 0; // This is the actual countdown (T+1)
        this.dutySequencePosition = 0;
        this.currentVolume = 0;
        this.lengthCounter = 0;
        this.isEnabled = false;
        this.envelopeStartFlag = false;
        this.envelopeDecayLevel = 0;
        this.envelopeDividerCounter = 0;
        this.sweepReloadFlag = false;
        this.sweepDividerCounter = 0;
        this.sweepMuting = false;
        this.isPulse1 = false; // Default to false, can be set by APU if needed
    }

    public void writeRegister(int register, byte value) {
        switch (register) {
            case 0: // $4000 or $4004 - Control / Volume / Envelope
                this.dutyCycle = (value >> 6) & 0x03;
                this.lengthCounterHalt = (value & 0x20) != 0; // Also envelope loop
                this.constantVolume = (value & 0x10) != 0;    // Also envelope disable
                this.envelopePeriodVolume = value & 0x0F;
                this.envelopeStartFlag = true; // Signal to reset envelope parameters
                break;
            case 1: // $4001 or $4005 - Sweep
                this.sweepEnabledReg = (value & 0x80) != 0;
                this.sweepPeriodReg = (value >> 4) & 0x07;
                this.sweepNegateReg = (value & 0x08) != 0;
                this.sweepShiftReg = value & 0x07;
                this.sweepReloadFlag = true; // Signal to reload sweep parameters
                break;
            case 2: // $4002 or $4006 - Timer Low
                this.timerLow = value & 0xFF;
                updateTimerPeriod();
                break;
            case 3: // $4003 or $4007 - Timer High / Length Counter Load
                this.timerHigh = value & 0x07;
                this.lengthCounterLoad = (value >> 3) & 0x1F;
                updateTimerPeriod();
                if (isEnabled) { // Only load if channel is enabled
                    this.lengthCounter = LengthCounterTable.LENGTH_TABLE[this.lengthCounterLoad];
                }
                this.envelopeStartFlag = true; // Writing to $4003/$4007 also resets envelope
                this.dutySequencePosition = 0; // Reset duty sequence
                break;
        }
    }

    private void updateTimerPeriod() {
        this.timerValue = (this.timerHigh << 8) | this.timerLow; // This is T
        // The timerCounter will be reloaded with T+1 in the clock() method when it reaches 0.
        // Some emulators might force a reload of timerCounter here if the channel is active.
        // For now, clock() handles the reload.
    }

    public void clock() { // This method is now assumed to be called by APU at the channel's timer rate (CPU_freq / 2)
        // The APU is responsible for calling this method at a rate of CPU_CLOCK / 2.

        // Proceed with timer logic.
        if (timerCounter > 0) {
            timerCounter--;
        } else {
            // Timer period is T from registers (timerValue). It counts T+1 of its own clock ticks.
            // Each tick for the pulse channel timer corresponds to 2 CPU cycles.
            // When timerCounter reaches 0, it's reloaded with timerValue + 1.
            timerCounter = this.timerValue + 1;
            dutySequencePosition = (dutySequencePosition + 1) % 8;
        }
    }

    public byte getSample() {
        if (!isEnabled || lengthCounter == 0 || sweepMuting) { // Added sweepMuting condition
            return 0;
        }

        byte currentDutyOutput = DUTY_SEQUENCES[this.dutyCycle][this.dutySequencePosition];

        if (currentDutyOutput == 0) {
            return 0;
        }

        int outputVolume;
        if (this.constantVolume) {
            outputVolume = this.envelopePeriodVolume;
        } else {
            outputVolume = this.envelopeDecayLevel;
        }
        return (byte) (outputVolume & 0x0F);
    }

    // Called by Frame Counter
    public void clockLengthCounter() {
        if (!lengthCounterHalt && lengthCounter > 0) {
            lengthCounter--;
        }
    }

    // Called by Frame Counter
    public void clockEnvelope() {
        if (this.envelopeStartFlag) {
            this.envelopeStartFlag = false;
            this.envelopeDecayLevel = 15; // Start at volume 15
            this.envelopeDividerCounter = this.envelopePeriodVolume + 1;
        } else {
            if (this.envelopeDividerCounter > 0) {
                this.envelopeDividerCounter--;
            } else {
                this.envelopeDividerCounter = this.envelopePeriodVolume + 1; // Reload divider
                if (this.envelopeDecayLevel > 0) {
                    this.envelopeDecayLevel--;
                } else if (this.lengthCounterHalt) { // If loop flag (lengthCounterHalt) is set
                    this.envelopeDecayLevel = 15; // Loop back to 15
                }
            }
        }
    }

    public void clockSweep() {
        if (this.sweepReloadFlag) {
            this.sweepDividerCounter = this.sweepPeriodReg + 1;
            this.sweepReloadFlag = false;
            // The current timer period (timerValue) is used for the first calculation.
            // Muting is also re-evaluated when sweep parameters change.
            this.sweepMuting = isSweepMuting();
        }

        if (this.sweepDividerCounter > 0) {
            this.sweepDividerCounter--;
        }

        if (this.sweepDividerCounter == 0) {
            this.sweepDividerCounter = this.sweepPeriodReg + 1; // Reload divider

            if (this.sweepEnabledReg && this.sweepShiftReg > 0 && !this.sweepMuting) {
                int currentTimerPeriod = this.timerValue;
                int changeAmount = currentTimerPeriod >> this.sweepShiftReg;
                int targetPeriod;

                if (this.sweepNegateReg) {
                    targetPeriod = currentTimerPeriod - changeAmount;
                    if (this.isPulse1) { // Sweep negate quirk for Pulse 1 ($4001)
                        targetPeriod--;
                    }
                } else {
                    targetPeriod = currentTimerPeriod + changeAmount;
                }

                if (targetPeriod > 0x7FF || currentTimerPeriod < 8) { // Check new muting condition
                    this.sweepMuting = true;
                } else {
                    // this.sweepMuting = false; // Muting is only set true here, cleared by isSweepMuting()
                    this.timerValue = targetPeriod;
                    this.timerLow = this.timerValue & 0xFF;
                    this.timerHigh = (this.timerValue >> 8) & 0x07;
                }
            }
        }
        // Update muting status based on current conditions, not just when calculation happens
        this.sweepMuting = isSweepMuting();
    }

    // Helper to determine if sweep should be muting
    private boolean isSweepMuting() {
        if (!this.sweepEnabledReg) return false; // Not muting if sweep is disabled
        if (this.timerValue < 8) return true; // Muting if current period < 8

        // Calculate target period without actually changing timerValue yet
        int currentTimerPeriod = this.timerValue;
        int changeAmount = currentTimerPeriod >> this.sweepShiftReg;
        int targetPeriod;
        if (this.sweepNegateReg) {
            targetPeriod = currentTimerPeriod - changeAmount;
            if (this.isPulse1) targetPeriod--;
        } else {
            targetPeriod = currentTimerPeriod + changeAmount;
        }
        if (targetPeriod > 0x7FF) return true; // Muting if target period > $7FF

        return false; // Not muting otherwise
    }

    public void setIsPulse1(boolean isPulse1) {
        this.isPulse1 = isPulse1;
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            this.lengthCounter = 0;
        }
        // Note: Enabling does not automatically reload length counter here.
        // That happens on a write to $4003/$4007.
    }

    public boolean isLengthCounterActive() {
        return this.lengthCounter > 0;
    }
}
