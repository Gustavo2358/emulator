import mapper.Mapper;
import java.util.logging.Logger;

public class Cartridge {
    private static final Logger logger = Logger.getLogger(Cartridge.class.getName());
    private static final int EIGHT_KB = 8192;
    private static final int SIXTEEN_KB = 16384;
    private static final int PRG_ROM_UNIT_SIZE = SIXTEEN_KB; // 16KB
    private static final int CHR_ROM_UNIT_SIZE = EIGHT_KB;  // 8KB

    // ROM and RAM data
    private final int[] prgRom;
    private final int[] prgRam;
    private final int[] chrRom;
    private int[] chrRam;

    // Configuration
    private final Mapper mapper;
    private final boolean useChrRam;
    private final boolean hasBatteryBackedRam;
    private final MirroringMode mirroringMode;

    public Cartridge(
            int[] prgRom,
            int[] chrRom,
            Mapper mapper,
            boolean hasBatteryBackedRam,
            MirroringMode mirroringMode) {

        // PRG ROM size must be a multiple of 16KB (16384 bytes)
        if (prgRom.length % PRG_ROM_UNIT_SIZE != 0) {
            throw new IllegalArgumentException("PRG ROM size must be a multiple of 16KB");
        }

        // CHR ROM size must be a multiple of 8KB (8192 bytes)
        // Note: CHR ROM can be zero length for some cartridges that use CHR RAM instead
        if (chrRom.length > 0 && chrRom.length % CHR_ROM_UNIT_SIZE != 0) {
            throw new IllegalArgumentException("CHR ROM size must be a multiple of 8KB");
        }

        this.prgRom = prgRom;
        this.chrRom = chrRom;
        this.mapper = mapper;
        useChrRam = chrRom.length == 0;
        this.hasBatteryBackedRam = hasBatteryBackedRam;
        this.mirroringMode = mirroringMode;

        this.prgRam = new int[EIGHT_KB];

    }

    public int cpuRead(int address) {
        if (address >= 0x8000 && address <= 0xFFFF) {
            int index = mapper.mapPrgRomAddress(address);
            if (index >= 0 && index < prgRom.length) {
                return prgRom[index];
            }
        } else if (address >= 0x6000 && address <= 0x7FFF) {
            return prgRam[address - 0x6000];
        }
        return 0;
    }

    //TODO handle this:
    /*
    Yes, in a more robust and flexible NES emulator design, it's generally better for the Mapper to handle the actual read and write operations for the cartridge's memory spaces (PRG ROM, PRG RAM, CHR ROM/RAM).

        Here's why:


        Encapsulation of Mapper Logic: Mappers can be complex. They don't just translate addresses; many have internal registers that control bank switching, mirroring, IRQs, etc. These registers are typically accessed via writes to specific addresses in the CPU's address space (often in the $8000-$FFFF range for PRG ROM control). If the mapper handles the write operations, it can directly process these register writes and update its internal state.
        Handling Writes to ROM Space: CPU writes to the PRG ROM address range ($8000-$FFFF) are often intended for mapper registers. If the Cartridge class simply tries to write to its prgRom array (which should be read-only), this logic is missed. A Mapper handling the write can distinguish between an attempt to write to ROM (which it might ignore) and a write to one of its control registers.
        PRG RAM Control: Some mappers also control PRG RAM banking or enable/disable PRG RAM. If the mapper handles reads/writes to the PRG RAM space ($6000-$7FFF), it can implement this logic. In your current Cartridge code, PRG RAM access bypasses the mapper.
        Simplified Cartridge Class: The Cartridge class can become simpler, primarily acting as a container for the ROM/RAM data and the mapper instance. It would delegate memory access operations to the mapper.
        To implement this, you would typically:


        Modify the Mapper interface to include methods like cpuRead(int address), cpuWrite(int address, int value), ppuRead(int address), and ppuWrite(int address, int value).
        Ensure that Mapper implementations have access to the necessary memory arrays (e.g., by passing them in the constructor or passing a reference to the Cartridge itself).
        Update the Cartridge class to call these methods on its mapper instance for the relevant address ranges.
        Ensure the CPUBus correctly routes all relevant CPU reads/writes (including those to $8000-$FFFF for mapper registers) to the Cartridge, which then delegates to the Mapper. Your current CPUBus does not seem to pass writes in the $8000-$FFFF range to the cartridge.
        While your current approach (mapper for address translation, cartridge for data access) can work for simple mappers like NROM, it becomes less tenable for more complex mappers. Delegating full read/write handling to the mapper provides a cleaner and more extensible design.
     */
    public void cpuWrite(int address, int value) {
        if (address >= 0x6000 && address <= 0x7FFF) {
            prgRam[address - 0x6000] = value;
        }
    }

    public int ppuRead(int address) {
        if (address >= 0x0000 && address <= 0x1FFF) {
            int index = mapper.mapChrRomAddress(address);

            if (useChrRam) {
                if (chrRam == null) {
                    chrRam = new int[CHR_ROM_UNIT_SIZE];
                }
                return index < chrRam.length ? chrRam[index] : 0;
            } else {
                return index < chrRom.length ? chrRom[index] : 0;
            }
        }
        return 0;
    }

    public void ppuWrite(int address, int value) {
        if (address >= 0x0000 && address <= 0x1FFF && useChrRam) {
            int index = mapper.mapChrRomAddress(address);
            if (chrRam == null) {
                chrRam = new int[CHR_ROM_UNIT_SIZE];
            }
            if (index >= 0 && index < chrRam.length) {
                chrRam[index] = value;
            }
        }
    }

    public byte[] getSaveData() {
        if (hasBatteryBackedRam) {
            byte[] saveData = new byte[prgRam.length];
            for (int i = 0; i < prgRam.length; i++) {
                saveData[i] = (byte) prgRam[i];
            }
            return saveData;
        }
        return new byte[0];
    }

    public void loadSaveData(byte[] saveData) {
        if (hasBatteryBackedRam && saveData.length == prgRam.length) {
            for (int i = 0; i < prgRam.length; i++) {
                prgRam[i] = saveData[i] & 0xFF;
            }
        }
    }

    public MirroringMode getMirroringMode() {
        return mirroringMode;
    }

    public int getMapperId() {
        return mapper.getId();
    }

    public static Cartridge fromNesFile(byte[] fileData) {
        logger.info("Processing NES ROM file of size " + fileData.length + " bytes");

        // Validate iNES header
        if (fileData.length < 16 ||
            fileData[0] != 'N' || fileData[1] != 'E' || fileData[2] != 'S' || fileData[3] != 0x1A) {
            logger.severe("Invalid NES ROM file format. Header validation failed.");
            throw new IllegalArgumentException("Invalid NES ROM file format");
        }

        logger.info("Valid iNES header found: 'NES\\u001A'");

        int prgRomSizeIn16kb = fileData[4];
        int chrRomSizeIn8kb = fileData[5];
        int flags6 = fileData[6];
        int flags7 = fileData[7];

        logger.info(String.format("ROM configuration: PRG ROM: %d x 16KB, CHR ROM: %d x 8KB",
                    prgRomSizeIn16kb, chrRomSizeIn8kb));
        logger.info(String.format("Flags: byte 6: 0x%02X, byte 7: 0x%02X", flags6, flags7));

        // Extract mapper ID (low nibble from byte 6, high nibble from byte 7)
        int mapperId = ((flags7 & 0xF0) | (flags6 >> 4)) & 0xFF;
        logger.info("Mapper ID: " + mapperId);

        boolean verticalMirroring = (flags6 & 0x01) == 0x01;
        boolean hasBatteryBackedRAM = (flags6 & 0x02) == 0x02;
        boolean hasTrainer = (flags6 & 0x04) == 0x04;
        boolean fourScreenVRAM = (flags6 & 0x08) == 0x08;

        logger.info(String.format("ROM features: Vertical mirroring: %b, Battery-backed RAM: %b, Trainer: %b, Four-screen VRAM: %b",
                    verticalMirroring, hasBatteryBackedRAM, hasTrainer, fourScreenVRAM));

        int trainerSize = hasTrainer ? 512 : 0;
        int prgRomSize = prgRomSizeIn16kb * PRG_ROM_UNIT_SIZE;
        int chrRomSize = chrRomSizeIn8kb * CHR_ROM_UNIT_SIZE;

        logger.info(String.format("Memory layout: PRG ROM: %d bytes, CHR ROM: %d bytes, Trainer: %d bytes",
                    prgRomSize, chrRomSize, trainerSize));

        int headerSize = 16;
        int[] prgRom = new int[prgRomSize];
        int prgStart = headerSize + trainerSize;
        logger.info("Loading PRG ROM data from offset " + prgStart);
        for (int i = 0; i < prgRomSize; i++) {
            prgRom[i] = fileData[prgStart + i] & 0xFF;
        }

        int[] chrRom;
        if (chrRomSize > 0) {
            chrRom = new int[chrRomSize];
            int chrStart = prgStart + prgRomSize;
            logger.info("Loading CHR ROM data from offset " + chrStart);
            for (int i = 0; i < chrRomSize; i++) {
                chrRom[i] = fileData[chrStart + i] & 0xFF;
            }
        } else {
            logger.info("No CHR ROM found, game will use CHR RAM");
            chrRom = new int[0];
        }

        MirroringMode mirroringMode;
        if (fourScreenVRAM) {
            mirroringMode = MirroringMode.FOUR_SCREEN;
        } else {
            mirroringMode = verticalMirroring ? MirroringMode.VERTICAL : MirroringMode.HORIZONTAL;
        }
        logger.info("Using mirroring mode: " + mirroringMode);

        Mapper mapper = MapperFactory.createMapper(mapperId, prgRomSize, chrRomSize);
        logger.info("Created mapper: " + mapper.getClass().getSimpleName() + " (ID: " + mapperId + ")");

        Cartridge cartridge = new Cartridge(prgRom, chrRom, mapper, hasBatteryBackedRAM, mirroringMode);
        logger.info("Cartridge loaded successfully");

        return cartridge;
    }
}
