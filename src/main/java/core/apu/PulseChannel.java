package core.apu;

public class PulseChannel {
    // Registers
    private int dutyCycle;              // 2 bits (0-3) -> $4000 SQ1_VOL / $4004 SQ2_VOL [D]
    private boolean lengthCounterHalt;  // 1 bit (envelope loop) -> $4000 SQ1_VOL / $4004 SQ2_VOL [L]
    private boolean constantVolume;     // 1 bit (envelope enable) -> $4000 SQ1_VOL / $4004 SQ2_VOL [V]
    private int envelopePeriodVolume;   // 4 bits (period for envelope or constant volume) -> $4000 SQ1_VOL / $4004 SQ2_VOL [N]

    private boolean sweepEnabled;       // 1 bit -> $4001 SQ1_SWEEP / $4005 SQ2_SWEEP [E]
    private int sweepPeriod;            // 3 bits -> $4001 SQ1_SWEEP / $4005 SQ2_SWEEP [P]
    private boolean sweepNegate;        // 1 bit -> $4001 SQ1_SWEEP / $4005 SQ2_SWEEP [N]
    private int sweepShift;             // 3 bits -> $4001 SQ1_SWEEP / $4005 SQ2_SWEEP [S]

    private int timerLow;               // 8 bits -> $4002 SQ1_LO / $4006 SQ2_LO
    private int timerHigh;              // 3 bits -> $4003 SQ1_HI / $4007 SQ2_HI [T high]
    private int lengthCounterLoad;      // 5 bits -> $4003 SQ1_HI / $4007 SQ2_HI [L]

    // Internal state
    private int timerValue;             // Current value of the 11-bit timer period
    private int timerCounter;           // Current countdown value for the timer
    private int dutySequencePosition;   // Current position in the duty cycle sequence (0-7)
    private int currentVolume;          // Current volume (considering envelope)
    private int lengthCounter;          // Length counter
    private boolean isEnabled;            // Channel enabled state (via $4015)


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
        this.sweepEnabled = false;
        this.sweepPeriod = 0;
        this.sweepNegate = false;
        this.sweepShift = 0;
        this.timerLow = 0;
        this.timerHigh = 0;
        this.lengthCounterLoad = 0;
        this.timerValue = 0; // This stores the period, not the countdown
        this.timerCounter = 0; // This is the actual countdown
        this.dutySequencePosition = 0;
        this.currentVolume = 0;
        this.lengthCounter = 0;
        this.isEnabled = false;
    }

    public void writeRegister(int register, byte value) {
        switch (register) {
            case 0: // $4000 or $4004 - Control / Volume / Envelope
                this.dutyCycle = (value >> 6) & 0x03;
                this.lengthCounterHalt = (value & 0x20) != 0; // Also envelope loop
                this.constantVolume = (value & 0x10) != 0;    // Also envelope disable
                this.envelopePeriodVolume = value & 0x0F;
                // If constantVolume is true, envelopePeriodVolume is the volume.
                // Otherwise, it's the envelope period.
                if (this.constantVolume) {
                    this.currentVolume = this.envelopePeriodVolume;
                } else {
                    // TODO: Reset envelope
                }
                break;
            case 1: // $4001 or $4005 - Sweep
                this.sweepEnabled = (value & 0x80) != 0;
                this.sweepPeriod = (value >> 4) & 0x07;
                this.sweepNegate = (value & 0x08) != 0;
                this.sweepShift = value & 0x07;
                // TODO: Reset sweep unit
                break;
            case 2: // $4002 or $4006 - Timer Low
                this.timerLow = value & 0xFF;
                updateTimerValue();
                break;
            case 3: // $4003 or $4007 - Timer High / Length Counter Load
                this.timerHigh = value & 0x07;
                this.lengthCounterLoad = (value >> 3) & 0x1F;
                updateTimerPeriod();
                if (isEnabled) { // Only load if channel is enabled
                    this.lengthCounter = LengthCounterTable.LENGTH_TABLE[this.lengthCounterLoad];
                }
                // TODO: Envelope is reset.
                this.dutySequencePosition = 0; // Reset duty sequence
                break;
        }
    }

    private void updateTimerPeriod() {
        this.timerValue = (this.timerHigh << 8) | this.timerLow;
        // Timer counter should also be reloaded when period changes, but this is typically done
        // when it reaches 0 or on specific events. For now, clock will handle reload.
    }

    public void clock() {
        // Pulse channel timer is clocked every *other* CPU cycle (i.e., every APU cycle)
        // The APU.clock() calls this method every CPU cycle. So we need a divider here, or adjust APU.
        // For now, assume APU handles calling this at the correct rate (e.g. APU has an internal divider)
        // or this clock() is expected to run at CPU speed and divide internally if needed for sub-components.
        // The current APU.clock() calls this at CPU speed.
        // The pulse timer itself counts down at CPU_CLK / 2.
        // Let's assume this clock() is called at CPU_CLK / 2 rate by the APU's main clock logic.
        // OR, if called at CPU rate, we count two calls to this clock() as one timer clock.
        // For simplicity with current APU structure, let this be the "tick" that may or may not decrement the timer.
        // The actual timer period is for these ticks.

        if (timerCounter > 0) {
            timerCounter--;
        } else {
            timerCounter = this.timerValue; // Reload timer counter with the period value
            // When timer output a clock, advance duty cycle sequence
            dutySequencePosition = (dutySequencePosition + 1) % 8;
        }
        // Note: Length Counter, Envelope, Sweep are clocked by Frame Counter, not here.
    }

    public byte getSample() {
        if (!isEnabled || lengthCounter == 0) {
            return 0;
        }
        // TODO: Sweep mute condition
        // TODO: Proper envelope volume

        byte currentDutyOutput = DUTY_SEQUENCES[this.dutyCycle][this.dutySequencePosition];

        if (currentDutyOutput == 0) {
            return 0;
        }
        // For now, use currentVolume which is set if constantVolume is true.
        // Otherwise, envelope logic should update currentVolume.
        return (byte) (this.currentVolume & 0x0F);
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
    }

    public void clockSweep() {
        // TODO: Implement sweep unit logic
        // Clock sweep divider. If divider reaches 0, calculate new period.
        // Update timerValue if sweep is enabled and conditions are met.
        // Mute channel if sweep results in invalid frequency.
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
