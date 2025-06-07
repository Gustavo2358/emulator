package core;

import ppu.PPU;
import apu.APU;

public class CPUBus implements Bus {
    private final WRAM wram;
    private final Cartridge cartridge;
    private final PPU ppu;
    private final APU apu;
    private Controller controller1;
    private Controller controller2;

    public CPUBus(WRAM wram, Cartridge cartridge, PPU ppu, APU apu) {
        this.wram = wram;
        this.cartridge = cartridge;
        this.ppu = ppu;
        this.apu = apu;
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
        } else if (address == 0x4016) { // core.Controller 1
            return controller1.read();
        } else if (address == 0x4017) { // core.Controller 2
            return controller2.read();
        } else if ((address >= 0x4000 && address <= 0x4015)) { // APU registers
            return apu.read(address);
        } else if (address == 0x4014) { // OAMDMA
            // OAMDMA read (typically not readable, or returns open bus)
            return 0;
        } else if (address >= 0x4020) { // Cartridge space starts typically from $4020 or $6000
            return cartridge.cpuRead(address);
        }
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
        } else if (address == 0x4017) { // core.Controller 2 Strobe / APU Frame Counter control
            controller2.write(value);
            // APU related write might also occur here.
        } else if (address >= 0x4000 && address <= 0x401F) { // APU/IO registers
            apu.write(address, value);
        } else if (address >= 0x6000) { // Cartridge PRG RAM ($6000-$7FFF) / PRG ROM ($8000-$FFFF)
                                        // Mapper might handle writes to ROM area for bank switching.
            cartridge.cpuWrite(address, value);
        }
        // Note: Writes to Expansion ROM ($4020-$5FFF) are often ignored or handled by specific cartridges.
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
}
