package apu;

class SquareChannel {
    private int timer;
    private int timerCounter;
    private boolean output;
    private int volume;

    void writeControl(int value) {
        volume = value & 0x0F;
    }

    void writeTimerLow(int value) {
        timer = (timer & 0xFF00) | (value & 0xFF);
    }

    void writeTimerHigh(int value) {
        timer = (timer & 0x00FF) | ((value & 0x07) << 8);
        timerCounter = timer;
    }

    byte nextSample() {
        if (timer == 0) return 0;
        if (--timerCounter <= 0) {
            timerCounter = timer;
            output = !output;
        }
        return (byte) (output ? (volume << 2) : 0);
    }
}
