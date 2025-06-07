package core.apu; // Changed package declaration

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
    private boolean irqAssertedByDMC = false;


    // Constructor
    public APU(CPUBus bus) { // Added CPUBus parameter
        this.bus = bus;
        // Initialize channel objects
        this.pulse1 = new PulseChannel();
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
    }

    // Placeholder methods for Pulse 1 channel - These might be removed or adapted
    // if direct register writes are handled through the main writeRegister method.
    // public void pulse1Enable(boolean enable) {
    //     // This logic will likely move into PulseChannel or be controlled by $4015
    // }

    // public void pulse1WriteRegister(int register, byte value) {
    //     // This is now handled by the main writeRegister method delegating to pulse1
    // }

    // Placeholder methods for Pulse 2 channel - Will be removed or adapted
    // public void pulse2Enable(boolean enable) {
    //     // TODO: Implement Pulse 2 enable/disable logic (likely via $4015)
    // }

    // public void pulse2WriteRegister(int register, byte value) {
    //     // This is now handled by the main writeRegister method delegating to pulse2
    // }

    // Placeholder methods for Triangle channel - Will be removed or adapted
    // public void triangleEnable(boolean enable) {
    //     // TODO: Implement Triangle enable/disable logic (likely via $4015)
    // }

    // public void triangleWriteRegister(int register, byte value) {
    //     // This is now handled by the main writeRegister method delegating to triangle
    // }

    // Placeholder methods for Noise channel - Will be removed or adapted
    // public void noiseEnable(boolean enable) {
    //     // TODO: Implement Noise enable/disable logic (likely via $4015)
    // }

    // public void noiseWriteRegister(int register, byte value) {
    //     // This is now handled by the main writeRegister method delegating to noise
    // }

    // Placeholder methods for DMC channel - Will be removed or adapted
    // public void dmcEnable(boolean enable) {
    //     // TODO: Implement DMC enable/disable logic (Likely via $4015)
    // }
    // public void dmcWriteRegister(int register, byte value) {
    //     // This is now handled by the main writeRegister method delegating to dmc
    // }


    // Placeholder methods for memory-mapped register access
    public byte readRegister(int address) {
        // System.out.printf("APU Read Register: Address=0x%04X%n", address);
        if (address == 0x4015) { // Status Register
            byte status = 0;
            if (pulse1.isLengthCounterActive()) status |= 0x01;
            if (pulse2.isLengthCounterActive()) status |= 0x02;
            if (triangle.isLengthCounterActive()) status |= 0x04;
            if (noise.isLengthCounterActive()) status |= 0x08;
            // Bit 4 for DMC active status (bytes remaining > 0) is more complex.
            // if (dmc.isActive()) status |= 0x10; // TODO: Implement dmc.isActive()
            if (dmc.isIRQAsserted()) status |= 0x80; // DMC IRQ
            // TODO: Frame IRQ status bit 6
            dmc.clearIRQ(); // IRQ flag is cleared on read of $4015
            return status;
        }
        // Other APU registers are generally not readable or return open bus.
        return 0;
    }

    public void writeRegister(int address, byte value) {
        // System.out.printf("APU Write Register: Address=0x%04X, Value=0x%02X%n", address, value);
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
        } else if (address == 0x4017) { // Frame Counter Control
            // TODO: Implement Frame Counter logic
            // System.out.println("Write to Frame Counter ($4017): " + String.format("0x%02X", value));
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
            sourceDataLine.write(buffer, 0, 1);
        }
    }

    /**
     * Clocks the APU. This method should be called by the CPU at its clock rate.
     * It handles internal APU timing, including downsampling for audio sample generation.
     */
    public void clock() {
        // This clock is the CPU clock (approx 1.79MHz)

        // Channels are clocked by the APU's internal ~894kHz clock (CPU_clock / 2)
        // However, the PulseChannel's internal timer is clocked every APU cycle (which is every 2 CPU cycles).
        // For now, let's call pulse1.clock() directly on every CPU clock for simplicity,
        // and its internal timer logic will handle the actual frequency.
        // More precise APU clocking (dividing CPU clock by 2 for some components) can be added later.
        pulse1.clock();
        pulse2.clock();
        triangle.clock();
        noise.clock();
        dmc.clock();
        // TODO: Clock Frame Counter here as well, which then clocks envelopes and length counters for P,T,N.

        // Check for DMC IRQ
        if (dmc.isIRQAsserted()) {
            // TODO: Signal IRQ to CPU. This might involve:
            // this.bus.cpu.triggerIRQ(IRQType.APU_DMC);
            // For now, just a flag or print.
            this.irqAssertedByDMC = true; // APU itself might hold this status for CPU to poll or direct line
             System.out.println("DMC IRQ Asserted");
        }


        // Audio sample generation timing (downsampling to SAMPLE_RATE)
        apuCycleAccumulator++;
        if (apuCycleAccumulator >= CPU_CYCLES_PER_AUDIO_SAMPLE) {
            apuCycleAccumulator -= CPU_CYCLES_PER_AUDIO_SAMPLE;
            byte mixedSample = generateSampleInternal();
            playSampleInternal(mixedSample);
        }
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
