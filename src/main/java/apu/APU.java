package apu;

public interface APU {
    int read(int address);
    void write(int address, int value);
    void runCycle();
    void reset();
}
