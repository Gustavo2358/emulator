package ppu;

import java.awt.*;

public interface PPU {
    int read(int address);

    void write(int address, int value);

    void reset();

    void runCycle();

    Image getFrameBuffer();
}
