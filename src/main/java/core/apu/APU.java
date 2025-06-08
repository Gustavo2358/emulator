package core.apu; // Changed package declaration

import core.CPUBus;

import javax.sound.sampled.*;

public class APU {

    // Audio format constants
    public static final float SAMPLE_RATE = 44100f;
    public static final int SAMPLE_SIZE_IN_BITS = 8;
    public static final int CHANNELS = 1; // Mono
    public static final boolean SIGNED = false; // Unsigned 8-bit PCM
    public static final boolean BIG_ENDIAN = false;
    public static final double CPU_SPEED = 1789773.0; // NES CPU speed in Hz (NTSC)
    private static final double CPU_CYCLES_PER_AUDIO_SAMPLE = CPU_SPEED / SAMPLE_RATE;

    private SourceDataLine sourceDataLine;
    private byte currentDummySampleValue = 0; // For dummy sample generation - will be removed later

    private double apuCycleAccumulator = 0.0; // Accumulates CPU cycles for audio sample timing

    // Member variables for each of the five channels
    private PulseChannel pulse1;
    private PulseChannel pulse2;
    private TriangleChannel triangle;
    private NoiseChannel noise;
    private DMCChannel dmc; // Changed from Object to DMCChannel

    private CPUBus bus; // Reference to the bus for DMC memory access

    // Frame Counter related fields
    private int frameSequenceCounter; // Counts CPU cycles for frame sequencer timing
    private int frameStep;            // Current step in the 4 or 5 step sequence
    private boolean sequenceMode;     // 0 for 4-step, 1 for 5-step
    private boolean irqInhibitFlag;   // True if frame IRQ is disabled
    private boolean frameInterruptFlag; // True if frame interrupt has occurred

    // NTSC CPU cycles for frame counter events (approximate)
    // Derived from 240Hz clocking. CPU runs at 1789773 Hz.
    // 1789773 / 240 = ~7457.38
    // Step 1: ~7457
    // Step 2: ~14913 (Step 1 + 7456)
    // Step 3: ~22371 (Step 2 + 7458)
    // Step 4 (4-step): ~29829 (Step 3 + 7458) -> Total ~29829, next cycle is reset
    // Step 4 (5-step): ~29829 (No audio clocks)
    // Step 5 (5-step): ~37281 (Step 4 + 7452) -> Total ~37281, next cycle is reset

    private static final int[] NTSC_FRAME_COUNTER_SEQUENCE_4_STEP = {7457, 14913, 22371, 29829};
    private static final int NTSC_FRAME_COUNTER_PERIOD_4_STEP = 29830; // Total cycles for 4-step sequence
    private static final int[] NTSC_FRAME_COUNTER_SEQUENCE_5_STEP = {7457, 14913, 22371, 29829, 37281};
    private static final int NTSC_FRAME_COUNTER_PERIOD_5_STEP = 37282; // Total cycles for 5-step sequence

    // Constructor
    public APU(CPUBus bus) { // Added CPUBus parameter
        this.bus = bus;
        // Initialize channel objects
        this.pulse1 = new PulseChannel();
        this.pulse1.setIsPulse1(true); // Designate this as Pulse 1 for sweep quirk
        this.pulse2 = new PulseChannel();
        this.triangle = new TriangleChannel();
        this.noise = new NoiseChannel();
        this.dmc = new DMCChannel(this.bus); // Instantiate DMCChannel with bus reference

        try {
            AudioFormat audioFormat = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Audio line not supported: " + audioFormat);
                return;
            }
            sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
            sourceDataLine.open(audioFormat);
            sourceDataLine.start();
            System.out.println("SourceDataLine initialized and started for APU.");
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            // Handle appropriately - e.g., disable audio, log error
            sourceDataLine = null;
        }
        this.frameSequenceCounter = 0;
        this.frameStep = 0;
        this.sequenceMode = false; // Default to 4-step
        this.irqInhibitFlag = true; // Default to IRQ inhibited
        this.frameInterruptFlag = false;
    }

    // Placeholder methods for memory-mapped register access
    public byte readRegister(int address) {
        // System.out.printf("APU Read Register: Address=0x%04X%n", address);
        if (address == 0x4015) { // Status Register
            byte status = 0;
            if (pulse1.isLengthCounterActive()) status |= 0x01;
            if (pulse2.isLengthCounterActive()) status |= 0x02;
            if (triangle.isLengthCounterActive()) status |= 0x04;
            if (noise.isLengthCounterActive()) status |= 0x08;
            if (dmc.isActive()) status |= 0x10; // DMC active status
            if (dmc.isIRQAsserted()) status |= 0x80; // DMC IRQ
            if (frameInterruptFlag) status |= 0x40; // Frame IRQ

            frameInterruptFlag = false; // Reading $4015 clears the frame interrupt flag
            // Reading $4015 does NOT clear the DMC IRQ flag. It's cleared by DMC itself or $4015 write disabling DMC.
            return status;
        }
        // Other APU registers are generally not readable or return open bus.
        return 0;
    }

    public void writeRegister(int address, byte value) {
        // System.out.printf("APU Write Register: Address=0x%04X, Value=0x%02X%n", address, value);
        // Log key register writes
        if (address == 0x4015) {
            System.out.printf("APU: Write to $4015 (Status/Enable): 0x%02X (P1:%b P2:%b T:%b N:%b D:%b)%n",
                value & 0xFF,
                (value & 0x01) != 0, (value & 0x02) != 0,
                (value & 0x04) != 0, (value & 0x08) != 0,
                (value & 0x10) != 0);
        } else if (address == 0x4017) {
            System.out.printf("APU: Write to $4017 (Frame Counter): 0x%02X (Mode:%s IRQInhibit:%b)%n",
                value & 0xFF,
                (value & 0x80) != 0 ? "5-step" : "4-step",
                (value & 0x40) != 0);
        }

        if (address >= 0x4000 && address <= 0x4003) { // Pulse 1 registers
            pulse1.writeRegister(address - 0x4000, value);
        } else if (address >= 0x4004 && address <= 0x4007) { // Pulse 2 registers
            pulse2.writeRegister(address - 0x4004, value);
        } else if (address >= 0x4008 && address <= 0x400B) { // Triangle registers
            triangle.writeRegister(address - 0x4008, value);
        } else if (address >= 0x400C && address <= 0x400F) { // Noise registers
            noise.writeRegister(address - 0x400C, value);
        } else if (address >= 0x4010 && address <= 0x4013) { // DMC registers
            dmc.writeRegister(address - 0x4010, value);
        } else if (address == 0x4015) { // Status Register (Channel Enable/Disable)
            pulse1.setEnabled((value & 0x01) != 0);
            pulse2.setEnabled((value & 0x02) != 0);
            triangle.setEnabled((value & 0x04) != 0);
            noise.setEnabled((value & 0x08) != 0);
            dmc.setEnabled((value & 0x10) != 0);
            if ((value & 0x10) == 0) { // If DMC is disabled, its IRQ is cleared
                dmc.clearIRQ();
            }
            // Writing to $4015 also clears DMC IRQ if DMC is disabled.
            // Frame interrupt flag is not affected by $4015 writes directly, only by $4017 or reading $4015.
        } else if (address == 0x4017) { // Frame Counter Control
            // System.out.println("Write to Frame Counter ($4017): " + String.format("0x%02X", value));
            this.sequenceMode = (value & 0x80) != 0; // Bit 7: 0 for 4-step, 1 for 5-step
            this.irqInhibitFlag = (value & 0x40) != 0; // Bit 6: 0 for IRQ enabled, 1 for IRQ disabled

            this.frameSequenceCounter = 0; // Writing to $4017 resets the frame counter and its step.
            this.frameStep = 0;
            if (this.irqInhibitFlag) {
                this.frameInterruptFlag = false; // Clear pending frame interrupt if inhibited
            }

            // If 5-step mode, clock all units immediately (half and quarter frame)
            // This is a side effect of $4017 write if mode is 5-step.
            if (this.sequenceMode) {
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
            }
        } else {
            // System.out.println("Unhandled APU register write: " + String.format("0x%04X", address));
        }
    }

    // Placeholder method for generating audio samples - becomes private
    private byte generateSampleInternal() {
        byte p1 = pulse1.getSample();
        byte p2 = pulse2.getSample();
        byte t = triangle.getSample();
        byte n = noise.getSample();
        byte d = dmc.getSample(); // DMC output is 0-127

        // System.out.printf("APU Samples: P1=%d, P2=%d, T=%d, N=%d, D=%d%n", p1, p2, t, n, d); // Log individual channel outputs

        // NES mixing is complex and non-linear. This is a placeholder.
        // Pulse output: 0-15. Triangle: 0-15. Noise: 0-15. DMC: 0-127.
        // A common simplified mixer:
        // pulse_out = 0.00752 * (p1 + p2)
        // tnd_out = 0.00851 * t + 0.00494 * n + 0.00335 * d
        // output = pulse_out + tnd_out (scaled to byte range)

        // Simpler sum for now, then scale.
        // Max raw sum: 15+15+15+15+127 = 187
        // Let's scale to make it somewhat audible but not clip excessively.
        // Max possible output of each channel is 15 for P,T,N and 127 for D.
        // If we scale P,T,N by ~2 and D by ~0.5, then sum:
        // (15*2) + (15*2) + (15*2) + (15*2) + (127*1) = 30+30+30+30+127 = 247. This fits in a byte.
        // For now, let's try (p1+p2)*coeff1 + (t+n)*coeff2 + d*coeff3
        // Or even simpler: ((p1+p2)*2 + t*2 + n*2 + d)
        // ( (15+15)*2 + 15*2 + 15*2 + 127 ) = (30*2 + 30 + 30 + 127) = (60 + 60 + 127) = 247
        int mixed = ((p1 + p2) * 2) + (t * 2) + (n * 2) + d;

        if (mixed > 255) mixed = 255;
        if (mixed < 0) mixed = 0;

        return (byte) mixed;
    }

    // Method to play a single sample - becomes private or internal if only called by clock
    private void playSampleInternal(byte sample) {
        if (sourceDataLine != null && sourceDataLine.isActive()) {
            byte[] buffer = {sample};
            // System.out.printf("APU Mixed Sample: %d (0x%02X)%n", sample & 0xFF, sample & 0xFF); // Log mixed sample played
            sourceDataLine.write(buffer, 0, 1);
        }
    }

    private void clockQuarterFrameUnits() {
        pulse1.clockEnvelope();
        pulse2.clockEnvelope();
        triangle.clockLinearCounter();
        noise.clockEnvelope();
    }

    private void clockHalfFrameUnits() {
        pulse1.clockLengthCounter();
        pulse1.clockSweep();
        pulse2.clockLengthCounter();
        pulse2.clockSweep();
        triangle.clockLengthCounter();
        noise.clockLengthCounter();
    }

    private void clockFrameCounter() {
        frameSequenceCounter++;

        if (!sequenceMode) { // 4-step sequence
            if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_4_STEP[0]) { // ~7457
                clockQuarterFrameUnits();
            } else if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_4_STEP[1]) { // ~14913
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
            } else if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_4_STEP[2]) { // ~22371
                clockQuarterFrameUnits();
            } else if (frameSequenceCounter >= NTSC_FRAME_COUNTER_PERIOD_4_STEP) { // ~29829 (end of step), reset on 29830
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
                if (!irqInhibitFlag) {
                    frameInterruptFlag = true;
                    // Signal actual IRQ to CPU if frameInterruptFlag is set
                    if (this.bus != null && this.bus.getCpu() != null) {
                        this.bus.getCpu().assertIRQLine();
                    }
                }
                frameSequenceCounter = 0;
            }
        } else { // 5-step sequence
            if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_5_STEP[0]) { // ~7457
                clockQuarterFrameUnits();
            } else if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_5_STEP[1]) { // ~14913
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
            } else if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_5_STEP[2]) { // ~22371
                clockQuarterFrameUnits();
            } else if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_5_STEP[3]) { // ~29829
                // No audio clocks on this step in 5-step mode
            } else if (frameSequenceCounter >= NTSC_FRAME_COUNTER_PERIOD_5_STEP) { // ~37281 (end of step), reset on 37282
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
                // No IRQ in 5-step mode according to many sources
                frameSequenceCounter = 0;
            }
        }
    }

    /**
     * Clocks the APU. This method should be called by the CPU at its clock rate.
     * It handles internal APU timing, including downsampling for audio sample generation.
     */
    public void clock() {
        // This clock is the CPU clock (approx 1.79MHz)

        // Clock the Frame Counter first, as it might trigger other clocks
        clockFrameCounter();

        // Channels are clocked by the APU's internal ~894kHz clock (CPU_clock / 2)
        // However, the PulseChannel's internal timer is clocked every APU cycle (which is every 2 CPU cycles).
        // For now, let's call pulse1.clock() directly on every CPU clock for simplicity,
        // and its internal timer logic will handle the actual frequency.
        // More precise APU clocking (dividing CPU clock by 2 for some components) can be added later.
        pulse1.clock();
        pulse2.clock();
        triangle.clock(); // Triangle timer is clocked at CPU rate
        noise.clock();    // Noise timer is clocked at CPU rate

        // Handle DMC stall request before clocking DMC
        if (dmc.hasPendingStallRequest()) {
            if (bus != null && bus.getCpu() != null) { // Ensure bus and cpu are available
                bus.getCpu().stallForDMA(3); // Stall CPU for 3 cycles (placeholder)
            }
            dmc.clearPendingStallRequestAndSetNeedsFetch();
            // DMC will fetch on its dmc.clock() call now
        }
        dmc.clock();      // DMC timer is clocked at CPU rate

        // Check for DMC IRQ

        // Audio sample generation timing (downsampling to SAMPLE_RATE)
        apuCycleAccumulator++;
        if (apuCycleAccumulator >= CPU_CYCLES_PER_AUDIO_SAMPLE) {
            apuCycleAccumulator -= CPU_CYCLES_PER_AUDIO_SAMPLE;
            byte mixedSample = generateSampleInternal();
            playSampleInternal(mixedSample);
        }
    }

    // --- IRQ Related Methods for CPU Polling ---

    public boolean isDmcIrqAsserted() {
        return dmc.isIRQAsserted();
    }

    public void clearDmcIrq() {
        // DMC IRQ is typically cleared by the DMC channel itself when conditions are met
        // (e.g., $4010 write disabling IRQ, or $4015 disabling DMC channel).
        // This method can be called if CPU needs to explicitly acknowledge/clear APU's view if necessary,
        // but primary responsibility is in DMCChannel and game logic.
        dmc.clearIRQ(); // Propagate to DMC channel
    }

    public boolean isFrameIrqAsserted() {
        return frameInterruptFlag;
    }

    public void clearFrameIrq() {
        // Frame IRQ is cleared by reading $4015 or by writing to $4017 that inhibits IRQs.
        this.frameInterruptFlag = false;
    }

    // Call this when shutting down the emulator
    public void stopAudio() {
        if (sourceDataLine != null) {
            sourceDataLine.drain();
            sourceDataLine.stop();
            sourceDataLine.close();
            System.out.println("SourceDataLine stopped and closed.");
        }
    }
    // TODO: Add any other necessary methods and member variables
}
