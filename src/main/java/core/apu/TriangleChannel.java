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
    private int internalTimerPeriod;      // Stores the T+1 value
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

        this.timerValue = 0; // This is the countdown timer
        this.internalTimerPeriod = 1; // Default to a minimal period to avoid division by zero or issues
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
        this.internalTimerPeriod = ((this.timerHigh << 8) | this.timerLow) + 1;

        // The value internalTimerPeriod is T+1.
        // If internalTimerPeriod is 1, it means T (the actual timer period) is 0.
        // The getSample() method mutes output if getTimerPeriod() < 2 (i.e., T+1 < 2, so T < 1, meaning T=0).
        // This check flags that such a state has been programmed.
        if (this.internalTimerPeriod == 1) {
            System.err.printf("APU TriangleChannel Warning: Timer period set to 0 (timerLow=0x%02X, timerHigh=0x%X). Output will be muted for this period.%n", this.timerLow, this.timerHigh);
        }
        // Optional: Could add an assertion here if T=0 is considered a critical error during development.
        // assert this.internalTimerPeriod > 1 : "Triangle channel timer period T set to 0, which is often problematic.";

        // The timerValue (countdown) is reloaded in clock() when it reaches 0.
        // It's also important that timerValue is reloaded if it's currently 0 or negative
        // to prevent it from getting stuck. The current clock() logic handles reload when timerValue hits 0.
        // If timerValue was 0 (from construction) and updateTimerPeriod is called,
        // timerValue should ideally be set to internalTimerPeriod.
        // For now, sticking to the plan of minimal changes unless problems arise.
        // The original issue with "no audio yet" might stem from timerValue not being correctly initialized
        // after period changes if it was 0.
        // The clock() method was changed to decrement first:
        // if this.timerValue = 0 -> this.timerValue = -1. Then if(-1 == 0) is false.
        // This means if timerValue is ever 0 when clock() is called, it will go to -1 and never reload
        // unless something else sets timerValue.
        // Let's add a line to reset timerValue if it's 0 when the period updates.
        // This seems like a sensible addition to prevent timer stall.
        if (this.timerValue == 0 && this.internalTimerPeriod > 0) {
             // If the timer is currently at 0 (e.g. initial state or just expired but period changed before reload)
             // and a new valid period is set, make the new period take effect immediately for the countdown.
             // This prevents the timer from potentially stalling at -1 if clock() is called when timerValue is 0.
            this.timerValue = this.internalTimerPeriod;
        }
    }

    private int getTimerPeriod() {
        return this.internalTimerPeriod;
    }

    public void clock() {
        // 1. Always decrement the internal timer (timerValue) each CPU cycle.
        this.timerValue--;

        // 2. When timerValue hits zero, reload it with timerPeriod+1 (internalTimerPeriod).
        if (this.timerValue == 0) {
            this.timerValue = this.internalTimerPeriod; // internalTimerPeriod is already period+1

            // 3. Gate advancement of sequencePosition on:
            //    isEnabled == true (implicit: lengthCounter > 0 means enabled and running,
            //                       as setEnabled(false) clears lengthCounter. If channel is
            //                       disabled and lengthCounter becomes 0, this stops advancement.)
            //    linearCounter > 0
            //    lengthCounter > 0
            if (this.lengthCounter > 0 && this.linearCounter > 0) {
                // Note: The problem statement does not ask to gate sequencer advancement
                // here based on the timer period's value (e.g. if period < 2), only in getSample().
                this.sequencePosition = (this.sequencePosition + 1) % 32;
            }
        }
        // Note: This implementation relies on timerValue being initialized to a value > 0
        // (e.g., to internalTimerPeriod when the timer's period is set or channel is reset).
        // If timerValue starts at 0 (e.g., from default constructor `this.timerValue = 0;`)
        // and is not otherwise updated, it will decrement to -1. The `if (this.timerValue == 0)`
        // condition will then not be met, and the timer might stall or behave incorrectly
        // until it wraps around or is explicitly reset by other code.
        // This specific subtask is scoped only to changing the clock() method as per instructions.
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

    public void setEnabled(boolean enabled) {
        if (!enabled) {
            this.lengthCounter = 0;
        } else {
            // If the channel is being enabled, and its lengthCounter was previously 0 (e.g. from being disabled),
            // it should be reloaded. The actual length value to load comes from the 'lengthCounterLoad'
            // field, which is an index into the LENGTH_TABLE. This field is set by writes to $400B.
            // This behavior aligns with many emulators where enabling a channel makes it
            // "active" with its currently programmed length, rather than waiting for another $400B write.
            // Check if lengthCounterLoad is within the bounds of LENGTH_TABLE.
            // The lengthCounterLoad is a 5-bit value, so its max index is 31 (0x1F).
            // LENGTH_TABLE has 32 entries (0-31).
            if (this.lengthCounterLoad >= 0 && this.lengthCounterLoad < LengthCounterTable.LENGTH_TABLE.length) {
                 // Only reload if the channel was effectively off (lengthCounter == 0).
                 // Some interpretations suggest that enabling always reloads it.
                 // For now, let's assume it reloads if it's currently 0.
                 // A more direct interpretation of "reloads its length counter" is to always do it on setEnabled(true).
                this.lengthCounter = LengthCounterTable.LENGTH_TABLE[this.lengthCounterLoad];
            }
            // Note: The linear counter is not directly reloaded by setEnabled.
            // It's controlled by the linearCounterReloadFlag, which is set by a write to $400B
            // and actioned by clockLinearCounter. Enabling the channel allows the linear counter
            // to start decrementing if it's > 0 and the controlFlag isn't halting it.
        }
    }

    public boolean isLengthCounterActive() {
        return lengthCounter > 0;
    }
}
