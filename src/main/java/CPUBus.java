import ppu.PPU;

public class CPUBus implements Bus {
    private final WRAM wram;
    private final Cartridge cartridge;
    private final PPU ppu;
    private Controller controller1;
    private Controller controller2;


    public CPUBus(WRAM wram, Cartridge cartridge, PPU ppu) {
        this.wram = wram;
        this.cartridge = cartridge;
        this.ppu = ppu;
        this.controller1 = new Controller();
        this.controller2 = new Controller();
    }

    @Override
    public int read(int address) {
        if (address < 0x2000) {
            return wram.read(address);
        } else if (address < 0x4000) {
            return ppu.read(address);
        } else if (address == 0x4016) {
            return controller1.read();
        } else if (address == 0x4017) {
            return controller2.read();
        } else if (address < 0x6000) {
            return cartridge.cpuRead(address);
        } else if (address < 0x10000) { // 0x6000 - 0xFFFF
            return cartridge.cpuRead(address);
        }
        return 0;
    }

    @Override
    public void write(int address, int value) {
        if (address < 0x2000) {
            wram.write(address, value);
        } else if (address < 0x4000) {
            ppu.write(address, value);
        } else if (address == 0x4016) {
            controller1.write(value);
        } else if (address == 0x4017) {
            controller2.write(value);
        } else if (address < 0x6000) {
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
}
