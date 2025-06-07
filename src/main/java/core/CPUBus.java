package core;

import core.apu.APU; // Updated import
import ppu.PPU;

public class CPUBus implements Bus {
    private final WRAM wram;
    private final Cartridge cartridge;
    private final PPU ppu;
    private final APU apu; // APU instance from core.apu package
    private Controller controller1;
    private Controller controller2;

    public CPUBus(WRAM wram, Cartridge cartridge, PPU ppu) {
        this.wram = wram;
        this.cartridge = cartridge;
        this.ppu = ppu;
        this.apu = new APU(this); // Instantiate core.apu.APU, passing CPUBus reference
        this.controller1 = new Controller();
        this.controller2 = new Controller();
    }

    @Override
    public int read(int address) {
        address &= 0xFFFF; // Ensure address is within 16-bit range
        if (address < 0x2000) { // $0000-$1FFF: core.WRAM
            return wram.read(address);
        } else if (address < 0x4000) { // $2000-$3FFF: PPU registers
            return ppu.read(address);
        } else if (address >= 0x4000 && address <= 0x4017) { // APU registers including $4016, $4017
            if (address == 0x4016) { // Controller 1
                return controller1.read();
            } else if (address == 0x4017) { // Controller 2
                // For reads, this is typically just controller 2. APU's $4017 is write-only (frame counter).
                return controller2.read();
            }
            // APU specific registers. $4015 is readable. Others might be open bus on read.
            return apu.readRegister(address);
        } else if (address >= 0x4018 && address <= 0x401F) { // Disabled APU/IO range
            // These are typically open bus or unmapped.
            // System.out.printf("CPUBus: Read from disabled APU/IO $%04X (open bus)\n", address);
            return 0; // Open bus behavior
        } else if (address >= 0x4020) { // Cartridge space starts typically from $4020 or $6000
            return cartridge.cpuRead(address);
        }
        // Note: $4014 (OAMDMA) is write-only. Reads usually return open bus or last written value to internal bus,
        // but for simplicity, we can treat it as unmapped here if not explicitly handled by APU or PPU.
        // The original code had a specific check for 0x4014 returning 0, which is fine.
        if (address == 0x4014) return 0; // OAMDMA read (typically not readable)

        return 0; // Default for unhandled reads or open bus behavior
    }

    @Override
    public void write(int address, int value) {
        address &= 0xFFFF; // Ensure address is within 16-bit range
        value &= 0xFF;   // Ensure value is a byte

        if (address < 0x2000) { // $0000-$1FFF: core.WRAM (mirrored every 0x800 bytes)
            wram.write(address, value);
        } else if (address >= 0x2000 && address < 0x4000) { // $2000-$3FFF: PPU registers (mirrored every 8 bytes)
            ppu.write(address, value);
        } else if (address == 0x4014) { // OAMDMA register
            ppu.startOAMDMA(value); // 'value' is the high byte of the source CPU RAM address (page number)
        } else if (address == 0x4016) { // core.Controller 1 Strobe
            controller1.write(value);
        } else if (address >= 0x4000 && address <= 0x4017) { // APU registers ($4000-$4013, $4015, $4017)
                                                            // $4017 is also Controller 2 strobe
            if (address == 0x4017) {
                controller2.write(value); // Controller 2 strobe
                // Also pass to APU for Frame Counter
            }
            apu.writeRegister(address, value);
        } else if (address >= 0x4018 && address <= 0x401F) { // Disabled APU/IO range
            // Writes to this range are typically ignored.
            // System.out.printf("CPUBus: Write to disabled APU/IO $%04X = $%02X (ignored)\n", address, value);
        } else if (address >= 0x6000) { // Cartridge PRG RAM ($6000-$7FFF) / PRG ROM ($8000-$FFFF)
                                        // Mapper might handle writes to ROM area for bank switching.
            cartridge.cpuWrite(address, value);
        }
        // Note: Writes to Expansion ROM ($4020-$5FFF) are often ignored or handled by specific cartridges.
        // The original code had a general $4000-$401F block. This is now more specific.
    }

    @Override
    public void loadWRamState(WRAM wram) {
        this.wram.loadMemoryState(wram);
    }

    public Controller getController1() {
        return controller1;
    }

    public Controller getController2() {
        return controller2;
    }

    public void setController1(Controller controller1) {
        this.controller1 = controller1;
    }

    public void setController2(Controller controller2) {
        this.controller2 = controller2;
    }

    public APU getAPU() {
        return apu;
    }
}
