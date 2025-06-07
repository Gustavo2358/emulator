package ppu;

import core.Bus;
import java.awt.*;

public interface PPU {
    int read(int address);

    void write(int address, int value);

    void reset();

    void runCycle();

    Image getFrameBuffer();

    /**
     * Sets the CPU bus for the PPU to use, particularly for OAMDMA reads from CPU RAM.
     * @param bus The CPU bus instance.
     */
    void setCpuBus(Bus bus);

    /**
     * Initiates the OAM DMA transfer.
     * The PPU will read 256 bytes from the specified page in CPU memory
     * (address (page << 8) to (page << 8) + 0xFF) and write it to its OAM.
     * This method should also trigger the necessary CPU stall and manage PPU state during DMA.
     * @param page The page number in CPU memory (0x00-0xFF) from which to read sprite data.
     */
    void startOAMDMA(int page);
}
