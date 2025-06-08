package core.apu;

import core.CPUBus;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Queue;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

public class APU {

    // --- NEW: OpenAL variables ---
    private long audioDevice;
    private long audioContext;
    private int alSource;
    private final int NUM_BUFFERS = 4; // Use a pool of buffers for smooth streaming
    private final int BUFFER_SIZE_SAMPLES = 4096; // Size of each buffer in samples
    private final Queue<Integer> availableBuffers = new LinkedList<>();
    private final ByteBuffer audioBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_SAMPLES).order(ByteOrder.nativeOrder());
    private int samplesInCurrentBuffer = 0;

    // --- UNCHANGED: APU simulation variables ---
    public static final float SAMPLE_RATE = 44100f;
    public static final double CPU_SPEED = 1789773.0; // NES CPU speed in Hz (NTSC)
    private static final double CPU_CYCLES_PER_AUDIO_SAMPLE = CPU_SPEED / SAMPLE_RATE;

    private double apuCycleAccumulator = 0.0; // Accumulates CPU cycles for audio sample timing

    // Member variables for each of the five channels
    private PulseChannel pulse1;
    private PulseChannel pulse2;
    private TriangleChannel triangle;
    private NoiseChannel noise;
    private DMCChannel dmc;

    private CPUBus bus; // Reference to the bus for DMC memory access

    // Frame Counter related fields
    private int frameSequenceCounter; // Counts CPU cycles for frame sequencer timing
    private int frameStep;            // Current step in the 4 or 5 step sequence
    private boolean sequenceMode;     // 0 for 4-step, 1 for 5-step
    private boolean irqInhibitFlag;   // True if frame IRQ is disabled
    private boolean frameInterruptFlag; // True if frame interrupt has occurred
    private core.CPU cpu; // Reference to CPU for IRQ

    private boolean apuHalfClockToggle = false; // For CPU/2 clocking

    private static final int[] NTSC_FRAME_COUNTER_SEQUENCE_4_STEP = {7457, 14913, 22371, 29829};
    private static final int NTSC_FRAME_COUNTER_PERIOD_4_STEP = 29830;
    private static final int[] NTSC_FRAME_COUNTER_SEQUENCE_5_STEP = {7457, 14913, 22371, 29829, 37281};
    private static final int NTSC_FRAME_COUNTER_PERIOD_5_STEP = 37282;

    public APU() {
        this.pulse1 = new PulseChannel();
        this.pulse1.setIsPulse1(true);
        this.pulse2 = new PulseChannel();
        this.triangle = new TriangleChannel();
        this.noise = new NoiseChannel();
        this.dmc = new DMCChannel();

        initOpenAL(); // New initialization method

        this.frameSequenceCounter = 0;
        this.frameStep = 0;
        this.sequenceMode = false;
        this.irqInhibitFlag = true;
        this.frameInterruptFlag = false;
    }

    // --- NEW: OpenAL Initialization ---
    private void initOpenAL() {
        audioDevice = alcOpenDevice((ByteBuffer) null);
        if (audioDevice == 0L) {
            throw new IllegalStateException("Failed to open the default audio device.");
        }

        ALCCapabilities alcCapabilities = ALC.createCapabilities(audioDevice);
        audioContext = alcCreateContext(audioDevice, (java.nio.IntBuffer) null); // Pass null for attributes
        if (audioContext == 0L) {
            alcCloseDevice(audioDevice); // Clean up device if context creation fails
            throw new IllegalStateException("Failed to create an OpenAL context.");
        }

        if (!alcMakeContextCurrent(audioContext)) {
            alcDestroyContext(audioContext);
            alcCloseDevice(audioDevice);
            throw new IllegalStateException("Failed to make OpenAL context current.");
        }
        AL.createCapabilities(alcCapabilities);

        alSource = alGenSources();
        if (alGetError() != AL_NO_ERROR) {
            // Proper cleanup before throwing
            alcMakeContextCurrent(0); // Release context
            alcDestroyContext(audioContext);
            alcCloseDevice(audioDevice);
            throw new RuntimeException("Failed to generate OpenAL source: " + alGetError());
        }

        for (int i = 0; i < NUM_BUFFERS; i++) {
            int bufferId = alGenBuffers();
            if (alGetError() != AL_NO_ERROR) {
                // Proper cleanup before throwing
                alDeleteSources(alSource);
                availableBuffers.forEach(AL10::alDeleteBuffers); // Reverted to method reference
                alcMakeContextCurrent(0);
                alcDestroyContext(audioContext);
                alcCloseDevice(audioDevice);
                throw new RuntimeException("Failed to generate OpenAL buffer: " + alGetError());
            }
            availableBuffers.add(bufferId);
        }
        System.out.println("APU: OpenAL initialized successfully with " + NUM_BUFFERS + " buffers.");
    }

    public void setBus(CPUBus bus) {
        this.bus = bus;
        if (this.dmc != null) {
            this.dmc.setBus(bus);
        }
    }

    public void setCpu(core.CPU cpu) {
        this.cpu = cpu;
    }

    public byte readRegister(int address) {
        if (address == 0x4015) {
            byte status = 0;
            if (pulse1.isLengthCounterActive()) status |= 0x01;
            if (pulse2.isLengthCounterActive()) status |= 0x02;
            if (triangle.isLengthCounterActive()) status |= 0x04;
            if (noise.isLengthCounterActive()) status |= 0x08;
            if (dmc.isActive()) status |= 0x10;
            if (dmc.isIRQAsserted()) status |= 0x80;
            if (frameInterruptFlag) status |= 0x40;
            frameInterruptFlag = false;
            return status;
        }
        return 0;
    }

    public void writeRegister(int address, byte value) {
        if (address >= 0x4000 && address <= 0x4003) {
            pulse1.writeRegister(address - 0x4000, value);
        } else if (address >= 0x4004 && address <= 0x4007) {
            pulse2.writeRegister(address - 0x4004, value);
        } else if (address >= 0x4008 && address <= 0x400B) {
            triangle.writeRegister(address - 0x4008, value);
        } else if (address >= 0x400C && address <= 0x400F) {
            noise.writeRegister(address - 0x400C, value);
        } else if (address >= 0x4010 && address <= 0x4013) {
            dmc.writeRegister(address - 0x4010, value);
        } else if (address == 0x4015) {
            pulse1.setEnabled((value & 0x01) != 0);
            pulse2.setEnabled((value & 0x02) != 0);
            triangle.setEnabled((value & 0x04) != 0);
            noise.setEnabled((value & 0x08) != 0);
            dmc.setEnabled((value & 0x10) != 0);
            if ((value & 0x10) == 0) {
                dmc.clearIRQ();
            }
        } else if (address == 0x4017) {
            this.sequenceMode = (value & 0x80) != 0;
            this.irqInhibitFlag = (value & 0x40) != 0;
            this.frameSequenceCounter = 0;
            this.frameStep = 0;
            if (this.irqInhibitFlag) {
                this.frameInterruptFlag = false;
            }
            if (this.sequenceMode) {
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
            }
        }
    }

    private byte generateSampleInternal() {
        byte p1 = pulse1.getSample();
        byte p2 = pulse2.getSample();
        byte t = triangle.getSample();
        byte n = noise.getSample();
        byte d = dmc.getSample();
        int mixed = ((p1 + p2) * 2) + (t * 2) + (n * 2) + d;
        if (mixed > 255) mixed = 255;
        if (mixed < 0) mixed = 0;
        return (byte) mixed;
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
        if (!sequenceMode) { // 4-step
            if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_4_STEP[0] ||
                frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_4_STEP[2]) {
                clockQuarterFrameUnits();
            } else if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_4_STEP[1]) {
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
            } else if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_4_STEP[3]) {
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
                if (!irqInhibitFlag) {
                    frameInterruptFlag = true;
                }
            }
            if (frameSequenceCounter >= NTSC_FRAME_COUNTER_PERIOD_4_STEP) {
                frameSequenceCounter = 0;
            }
        } else { // 5-step
            if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_5_STEP[0] ||
                frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_5_STEP[2]) {
                clockQuarterFrameUnits();
            } else if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_5_STEP[1]) {
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
            } else if (frameSequenceCounter == NTSC_FRAME_COUNTER_SEQUENCE_5_STEP[4]) {
                clockQuarterFrameUnits();
                clockHalfFrameUnits();
            }
            if (frameSequenceCounter >= NTSC_FRAME_COUNTER_PERIOD_5_STEP) {
                frameSequenceCounter = 0;
            }
        }
    }

    public void clock() {
        apuHalfClockToggle = !apuHalfClockToggle;
        clockFrameCounter();
        if (apuHalfClockToggle) {
            pulse1.clock();
            pulse2.clock();
        }
        triangle.clock();
        noise.clock();
        if (dmc.hasPendingStallRequest()) {
            if (bus != null && bus.getCpu() != null) {
                bus.getCpu().stallForDMA(4);
            }
            dmc.clearPendingStallRequestAndSetNeedsFetch();
        }
        dmc.clock();

        apuCycleAccumulator++;
        if (apuCycleAccumulator >= CPU_CYCLES_PER_AUDIO_SAMPLE) {
            apuCycleAccumulator -= CPU_CYCLES_PER_AUDIO_SAMPLE;
            byte mixedSample = generateSampleInternal();

            audioBuffer.put(mixedSample);
            samplesInCurrentBuffer++;

            if (samplesInCurrentBuffer == BUFFER_SIZE_SAMPLES) {
                flushAudioBuffer();
            }
        }
        unqueueProcessedBuffers();
    }

    private void flushAudioBuffer() {
        if (samplesInCurrentBuffer == 0 || availableBuffers.isEmpty()) {
            return;
        }

        int bufferId = availableBuffers.remove();
        audioBuffer.flip();
        alBufferData(bufferId, AL_FORMAT_MONO8, audioBuffer, (int) SAMPLE_RATE);
        alSourceQueueBuffers(alSource, bufferId);

        if (alGetSourcei(alSource, AL_SOURCE_STATE) != AL_PLAYING) {
            alSourcePlay(alSource);
        }

        audioBuffer.clear();
        samplesInCurrentBuffer = 0;
    }

    private void unqueueProcessedBuffers() {
        int processed = alGetSourcei(alSource, AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int bufferId = alSourceUnqueueBuffers(alSource);
            // Check for error after unqueueing
            if (alGetError() == AL_NO_ERROR) {
                 availableBuffers.add(bufferId);
            } else {
                System.err.println("APU: Error unqueueing OpenAL buffer.");
                // Potentially try to regenerate this buffer or handle error
            }
        }
    }

    public boolean isDmcIrqAsserted() {
        return dmc.isIRQAsserted();
    }

    public void clearDmcIrq() {
        dmc.clearIRQ();
    }

    public boolean isFrameIrqAsserted() {
        return frameInterruptFlag;
    }

    public void clearFrameIrq() {
        this.frameInterruptFlag = false;
    }

    public void stopAudio() {
        System.out.println("APU: Stopping audio...");
        flushAudioBuffer();

        // Stop the source and wait for it to finish all queued buffers
        alSourceStop(alSource);
        int processed = alGetSourcei(alSource, AL_BUFFERS_PROCESSED);
        while(processed > 0) {
            alSourceUnqueueBuffers(alSource); // Unqueue all remaining processed buffers
            processed--;
        }
        // Also unqueue any buffers that might have been queued but not yet processed
        int queued = alGetSourcei(alSource, AL_BUFFERS_QUEUED);
        while(queued > 0) {
            alSourceUnqueueBuffers(alSource);
            queued--;
        }

        alDeleteSources(alSource);
        if (alGetError() != AL_NO_ERROR) System.err.println("APU: Error deleting source: " + alGetError());

        while(!availableBuffers.isEmpty()){
            alDeleteBuffers(availableBuffers.remove());
        }

        if (audioContext != 0L) {
            alcMakeContextCurrent(0); // Release context
            alcDestroyContext(audioContext);
            audioContext = 0L;
        }
        if (audioDevice != 0L) {
            alcCloseDevice(audioDevice);
            audioDevice = 0L;
        }
        System.out.println("APU: OpenAL stopped and closed.");
    }
}
