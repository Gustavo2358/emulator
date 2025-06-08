package core;

import core.apu.APU; // Updated import
import ppu.PPU;

public class CPUBus implements Bus {
    private final WRAM wram;
    private final Cartridge cartridge;
    private final PPU ppu;
    private final APU apu; // APU instance
    private Controller controller1;
    private Controller controller2;
    private CPU cpu; // Reference to the CPU

    // Modified constructor to accept APU
    public CPUBus(WRAM wram, Cartridge cartridge, PPU ppu, APU apu) {
        this.wram = wram;
        this.cartridge = cartridge;
        this.ppu = ppu;
        this.apu = apu; // Initialize APU from parameter
        this.controller1 = new Controller();
        this.controller2 = new Controller();
    }

    public void setCpu(CPU cpu) {
        this.cpu = cpu;
    }

    public CPU getCpu() {
        return this.cpu;
    }

    @Override
    public int read(int address) {
        address &= 0xFFFF; // Ensure address is within 16-bit range
        if (address < 0x2000) { // $0000-$1FFF: core.WRAM
            return wram.read(address);
        } else if (address < 0x4000) { // $2000-$3FFF: PPU registers
            return ppu.read(address);
        } else if (address >= 0x4000 && address <= 0x401F) { // APU and I/O Registers ($4000-$401F)
            if (address == 0x4015) {
                return apu.readRegister(address); // APU status read - Changed from readStatusRegister()
            } else if (address == 0x4016) { // Controller 1 read
                return controller1 != null ? controller1.read() : 0;
            } else if (address == 0x4017) { // Controller 2 read / APU Frame Counter (mixed use)
                return controller2 != null ? controller2.read() : 0;
            } else if (address <= 0x4013) { // APU channel registers
                return apu.readRegister(address); // Delegate to APU for other readable registers in range
            }
            return 0; // Default for unhandled/unreadable registers in $4000-$401F
        } else if (address >= 0x4020) { // Cartridge space starts typically from $4020
            return cartridge.cpuRead(address);
        }
        return 0; // Open bus behavior for unmapped addresses outside defined ranges
    }

    @Override
    public void write(int address, int value) {
        address &= 0xFFFF; // Ensure address is within 16-bit range
        value &= 0xFF;   // Ensure value is a byte

        if (address < 0x2000) { // $0000-$1FFF: core.WRAM (mirrored every 0x800 bytes)
            wram.write(address, value);
        } else if (address < 0x4000) { // $2000-$3FFF: PPU registers (mirrored every 8 bytes)
            ppu.write(address, value);
        } else if (address >= 0x4000 && address <= 0x401F) { // APU and I/O Registers ($4000-$401F)
            if (address == 0x4014) { // OAMDMA register
                if (ppu != null) ppu.startOAMDMA(value); // Delegate to PPU
            } else if (address == 0x4016) { // Controller 1 Strobe / Data
                if (controller1 != null) controller1.write(value);
            } else if (address == 0x4017) { // APU Frame Counter Control / Controller 2 Strobe
                if (apu != null) apu.writeRegister(address, (byte) value); // APU Frame Counter
                if (controller2 != null) controller2.write(value); // Controller 2 strobe (if also used)
            } else if (address <= 0x4013 || address == 0x4015) { // Standard APU registers
                if (apu != null) apu.writeRegister(address, (byte) value);
            }
        } else if (address >= 0x4020) { // Cartridge space (often $6000+ or $8000+ depending on mapper)
            cartridge.cpuWrite(address, value);
        }
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
