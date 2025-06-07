package apu;

import javax.sound.sampled.*;

public class APUImpl implements APU {
    private static final int SAMPLE_RATE = 44100;
    private final SourceDataLine line;
    private final SquareChannel square1 = new SquareChannel();

    public APUImpl() {
        SourceDataLine tmp = null;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            tmp = AudioSystem.getSourceDataLine(format);
            tmp.open(format, SAMPLE_RATE / 60);
            tmp.start();
        } catch (Exception e) {
            System.err.println("Failed to init audio line: " + e.getMessage());
        }
        line = tmp;
    }

    @Override
    public int read(int address) {
        return 0; // Simplified
    }

    @Override
    public void write(int address, int value) {
        switch (address) {
            case 0x4000 -> square1.writeControl(value);
            case 0x4002 -> square1.writeTimerLow(value);
            case 0x4003 -> square1.writeTimerHigh(value);
        }
    }

    @Override
    public void runCycle() {
        if (line != null) {
            byte sample = square1.nextSample();
            line.write(new byte[]{sample}, 0, 1);
        }
    }

    @Override
    public void reset() {
        if (line != null) {
            line.flush();
        }
    }
}
