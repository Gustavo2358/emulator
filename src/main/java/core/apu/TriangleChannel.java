package core.apu;

package core.apu;

// Utility class for APU constants, could be in its own file.
class LengthCounterTable {
    public static final byte[] LENGTH_TABLE = {
            10, (byte) 254, 20, 2, 40, 4, 80, 6, (byte) 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, (byte) 192, 24, 72, 26, 16, 28, 32, 30
    };
}

public class TriangleChannel {
    // Registers
    private boolean controlFlag;          // True: Length counter halt, False: Linear counter control
    private int linearCounterReloadValue; // 7 bits ($4008 bits 0-6)

    // Timer low is not used by triangle channel directly like pulse/noise for period,
    // but $4009 is an unused register in the triangle's address space.
    // For simplicity, we can ignore $4009 writes or treat them as NOPs for now.

    private int timerLow;                 // 8 bits ($400A)
    private int timerHigh;                // 3 bits ($400B bits 0-2)
    private int lengthCounterLoad;        // 5 bits ($400B bits 3-7)

    // Internal state
    private int timerValue;               // Current value of the 11-bit timer (period)
    private int linearCounter;            // Current value of the linear counter
    private int lengthCounter;            // Current value of the length counter
    private int sequencePosition;         // Current step in the 32-step sequence (0-31)
    private boolean linearCounterReloadFlag; // Set when $4008 is written, cleared when linear counter is clocked with controlFlag clear

    // Triangle wave sequence (32 steps)
    // 0,1,2,3,4,5,6,7,8,9,A,B,C,D,E,F, F,E,D,C,B,A,9,8,7,6,5,4,3,2,1,0
    private static final byte[] TRIANGLE_SEQUENCE = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
    };

    public TriangleChannel() {
        this.controlFlag = false;
        this.linearCounterReloadValue = 0;
        this.timerLow = 0;
        this.timerHigh = 0;
        this.lengthCounterLoad = 0;

        this.timerValue = 0;
        this.linearCounter = 0;
        this.lengthCounter = 0;
        this.sequencePosition = 0;
        this.linearCounterReloadFlag = false;
    }

    public void writeRegister(int register, byte value) {
        switch (register) {
            case 0: // $4008 - Linear Counter Control / Reload Value
                this.controlFlag = (value & 0x80) != 0; // Bit 7: Length Counter Halt (true) / Linear Counter Control (false)
                this.linearCounterReloadValue = value & 0x7F; // Bits 0-6
                // Setting linearCounterReloadFlag is often mentioned here, though some sources say it's set by $400B.
                // For now, linearCounter itself is reloaded by clockLinearCounter if reload flag is set.
                break;
            case 1: // $4009 - Unused for triangle channel
                // No operation
                break;
            case 2: // $400A - Timer Low
                this.timerLow = value & 0xFF;
                updateTimerPeriod();
                break;
            case 3: // $400B - Timer High / Length Counter Load
                this.timerHigh = value & 0x07;
                this.lengthCounterLoad = (value >> 3) & 0x1F; // Bits 3-7
                updateTimerPeriod();
                if (!controlFlag) { // If length counter is not halted (i.e. active)
                    this.lengthCounter = LengthCounterTable.LENGTH_TABLE[this.lengthCounterLoad];
                }
                this.linearCounterReloadFlag = true; // Linear counter to be reloaded
                break;
        }
    }

    private void updateTimerPeriod() {
        // Timer period for triangle is (timerHigh << 8) | timerLow + 1
        // This is what the timer counts down *from*.
        // The actual clocking of the sequencer happens when this timer reaches 0.
        // The value written to registers is T. The period is T+1.
        // For now, timerValue will store the loaded T value, and clock will handle T+1 logic.
        // This is slightly different from pulse where timerValue is the current countdown.
        // Let's keep it consistent: timerValue is the current countdown value.
        // So, when registers are written, the period is set.
        // The actual value loaded into the timer counter will be this period.
        // We will set an internal 'period' variable.
    }

    private int getTimerPeriod() {
        return ((this.timerHigh << 8) | this.timerLow) + 1;
    }


    public void clock() {
        // Timer unit: clocks sequencer
        // The triangle channel's timer is clocked by the CPU clock (approx 1.79 MHz).
        // Pulse channels' timers are clocked every *other* CPU clock.
        // Triangle channel's timer is clocked every CPU clock.
        // However, the problem description says APU.clock() is called by CPU,
        // and APU.clock() calls triangle.clock(). So this method is effectively
        // called at CPU clock rate.

        if (timerValue > 0) {
            timerValue--;
        } else {
            timerValue = getTimerPeriod(); // Reload timer with period T+1
            // Clock the sequencer if length counter and linear counter are non-zero
            if (lengthCounter > 0 && linearCounter > 0) {
                sequencePosition = (sequencePosition + 1) % 32;
            }
        }
        // Note: Linear Counter and Length Counter are clocked by Frame Counter, not here.
    }

    public byte getSample() {
        // Triangle channel output is muted if its timer period is less than 2 (some say 3).
        // This prevents very high, potentially aliasing frequencies.
        if (getTimerPeriod() < 2) { // Or < 3, check specific docs if issues arise
            return 0;
        }
        // If length counter or linear counter is 0, output is 0.
        // This is implicitly handled by the clock() method not advancing sequencer.
        // However, getSample() should also check this.
        if (lengthCounter == 0 || linearCounter == 0) {
            return 0;
        }
        return TRIANGLE_SEQUENCE[sequencePosition];
    }

    // Called by Frame Counter
    public void clockLengthCounter() {
        if (!controlFlag && lengthCounter > 0) { // If not halted and counter > 0
            lengthCounter--;
        }
    }

    // Called by Frame Counter
    public void clockLinearCounter() {
        if (linearCounterReloadFlag) {
            linearCounter = linearCounterReloadValue;
        } else if (linearCounter > 0) {
            linearCounter--;
        }
        if (!controlFlag) { // If control flag is clear (linear counter control is active)
            linearCounterReloadFlag = false; // Clear reload flag
        }
    }

    // Used by APU to enable/disable channel via $4015
    public void setEnabled(boolean enabled) {
        if (!enabled) {
            this.lengthCounter = 0;
        }
        // Enabling does not immediately reload length counter from $400B,
        // that happens on write to $400B or $400F (for pulse).
        // If channel is re-enabled, its length counter remains 0 until a $400B write.
    }

    public boolean isLengthCounterActive() {
        return lengthCounter > 0;
    }
}
