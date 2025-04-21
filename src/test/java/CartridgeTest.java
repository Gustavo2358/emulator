import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CartridgeTest {

    public static final int SIXTEEN_KB = 16384;
    public static final int EIGHT_KB = 8192;


    @Test
    void testConstructorValidSizes() {
        int[] prgRom = new int[SIXTEEN_KB];
        int[] chrRom = new int[EIGHT_KB];

        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        assertNotNull(cartridge);
    }

    @Test
    void testConstructorInvalidPrgRomSize() {
        // Invalid PRG ROM size (not multiple of 16KB)
        int[] prgRom = new int[10000];
        int[] chrRom = new int[EIGHT_KB];

        MockMapper mapper = new MockMapper();

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
            new Cartridge(prgRom, chrRom, mapper, false, MirroringMode.HORIZONTAL)
        );

        assertTrue(exception.getMessage().contains("PRG ROM size must be a multiple of 16KB"));
    }

    @Test
    void testConstructorInvalidChrRomSize() {
        int[] prgRom = new int[SIXTEEN_KB];
        int[] chrRom = new int[5000];

        MockMapper mapper = new MockMapper();

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
            new Cartridge(prgRom, chrRom, mapper, false, MirroringMode.HORIZONTAL)
        );

        assertTrue(exception.getMessage().contains("CHR ROM size must be a multiple of 8KB"));
    }

    @Test
    void testConstructorEmptyChrRom() {
        int[] prgRom = new int[SIXTEEN_KB];
        int[] chrRom = new int[0];

        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        assertNotNull(cartridge);
    }

    @Test
    void testCpuReadFromPrgRom() {
        int[] prgRom = createPrgRomWithPattern(SIXTEEN_KB);
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                new int[EIGHT_KB],
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        mapper.mappedPrgAddress = 0xFF;

        int result = cartridge.cpuRead(0x8000);
        assertEquals(0xFF, result);
    }

    @Test
    void testCpuReadFromPrgRam() {
        int[] prgRom = new int[SIXTEEN_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                new int[EIGHT_KB],
                mapper,
                true,
                MirroringMode.HORIZONTAL);

        cartridge.cpuWrite(0x6000, 0x42);

        int result = cartridge.cpuRead(0x6000);
        assertEquals(0x42, result);
    }

    @Test
    void testCpuReadOutOfRange() {
        int[] prgRom = new int[SIXTEEN_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                new int[EIGHT_KB],
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        // Out of range address
        int result = cartridge.cpuRead(0x5000);
        assertEquals(0, result);
    }

    @Test
    void testPpuReadFromChrRom() {
        int[] prgRom = new int[SIXTEEN_KB];
        int[] chrRom = createChrRomWithPattern(EIGHT_KB);
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        mapper.mappedChrAddress = 0xFF;

        int result = cartridge.ppuRead(0x1000);
        assertEquals(0xFF, result);
    }

    @Test
    void testPpuReadFromChrRam() {
        int[] prgRom = new int[SIXTEEN_KB];
        int[] chrRom = new int[0];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        mapper.mappedChrAddress = 0x100;
        cartridge.ppuWrite(0x1000, 0x55);

        int result = cartridge.ppuRead(0x1000);
        assertEquals(0x55, result);
    }

    @Test
    void testPpuReadOutOfRange() {
        int[] prgRom = new int[SIXTEEN_KB];
        int[] chrRom = new int[EIGHT_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        int result = cartridge.ppuRead(EIGHT_KB);
        assertEquals(0, result);
    }

    @Test
    void testGetSaveDataWithBatteryBackedRam() {
        int[] prgRom = new int[SIXTEEN_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                new int[EIGHT_KB],
                mapper,
                true,
                MirroringMode.HORIZONTAL);

        cartridge.cpuWrite(0x6000, 0x42);
        cartridge.cpuWrite(0x6001, 0xFF);

        byte[] saveData = cartridge.getSaveData();

        assertNotNull(saveData);
        assertEquals(EIGHT_KB, saveData.length);
        assertEquals(0x42, saveData[0] & 0xFF);
        assertEquals(0xFF, saveData[1] & 0xFF);
    }

    @Test
    void testGetSaveDataWithoutBatteryBackedRam() {
        int[] prgRom = new int[SIXTEEN_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                new int[EIGHT_KB],
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        byte[] saveData = cartridge.getSaveData();

        assertNotNull(saveData);
        assertEquals(0, saveData.length);
    }

    @Test
    void testLoadSaveData() {
        int[] prgRom = new int[SIXTEEN_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                new int[EIGHT_KB],
                mapper,
                true,
                MirroringMode.HORIZONTAL);

        // Create save data
        byte[] saveData = new byte[EIGHT_KB];
        saveData[0] = 0x42;
        saveData[1] = (byte)0xFF;

        cartridge.loadSaveData(saveData);

        assertEquals(0x42, cartridge.cpuRead(0x6000));
        assertEquals(0xFF, cartridge.cpuRead(0x6001));
    }

    @Test
    void testFromNesFileValidHeader() {
        byte[] fileData = createMockNesRom(1, 1, 0, false, false, false, MirroringMode.HORIZONTAL);

        Cartridge cartridge = Cartridge.fromNesFile(fileData);

        assertNotNull(cartridge);
        assertEquals(0, cartridge.getMapperId());
        assertEquals(MirroringMode.HORIZONTAL, cartridge.getMirroringMode());
    }

    @Test
    void testFromNesFileInvalidHeader() {
        byte[] fileData = new byte[16];
        fileData[0] = 'X';
        fileData[1] = 'Y';
        fileData[2] = 'Z';

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                Cartridge.fromNesFile(fileData));

        assertTrue(exception.getMessage().contains("Invalid NES ROM file format"));
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1, 0, HORIZONTAL", // No flags set, horizontal mirroring
        "1, 1, 1, VERTICAL",   // Vertical mirroring flag
        "1, 1, 8, FOUR_SCREEN" // Four screen VRAM flag
    })
    void testFromNesFileWithDifferentMirroringModes(int prgSize, int chrSize, int flags6, String expectedMode) {
        MirroringMode expectedMirroringMode = MirroringMode.valueOf(expectedMode);

        byte[] fileData = createMockNesRom(prgSize, chrSize, flags6, false, false, false,
                expectedMirroringMode);

        Cartridge cartridge = Cartridge.fromNesFile(fileData);

        assertEquals(expectedMirroringMode, cartridge.getMirroringMode());
    }

    @Test
    void testFromNesFileWithBatteryBackedRAM() {
        byte[] fileData = createMockNesRom(1, 1, 0x02, true, false, false, MirroringMode.HORIZONTAL);

        Cartridge cartridge = Cartridge.fromNesFile(fileData);

        // Write to PRG RAM
        cartridge.cpuWrite(0x6000, 0x42);

        // Get save data - should return non-empty array since battery-backed flag is true
        byte[] saveData = cartridge.getSaveData();
        assertEquals(EIGHT_KB, saveData.length);
        assertEquals(0x42, saveData[0] & 0xFF);
    }

    @Test
    void testFromNesFileWithTrainer() {
        byte[] fileData = createMockNesRom(1, 1, 0x04, false, true, false, MirroringMode.HORIZONTAL);

        Cartridge cartridge = Cartridge.fromNesFile(fileData);

        assertNotNull(cartridge);
    }

    @Test
    void testFromNesFileWithoutChrRom() {
        byte[] fileData = createMockNesRom(1, 0, 0, false, false, false, MirroringMode.HORIZONTAL);

        Cartridge cartridge = Cartridge.fromNesFile(fileData);

        assertNotNull(cartridge);

        // Write to CHR RAM
        cartridge.ppuWrite(0x1000, 0x42);

        // Read from CHR RAM
        assertEquals(0x42, cartridge.ppuRead(0x1000));
    }

    @Test
    void testWithMaximumAllowedPrgRomSize() {
        int maxPrgUnits = 64;
        int[] prgRom = new int[maxPrgUnits * SIXTEEN_KB];
        int[] chrRom = new int[EIGHT_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        assertNotNull(cartridge);
    }

    @Test
    void testWithMaximumAllowedChrRomSize() {
        int maxChrUnits = 128;
        int[] prgRom = new int[SIXTEEN_KB];
        int[] chrRom = new int[maxChrUnits * EIGHT_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        assertNotNull(cartridge);
    }

    @Test
    void testWithMultiUnitPrgRom() {
        // Test with 32KB PRG ROM (2 units)
        int[] prgRom = new int[2 * SIXTEEN_KB];
        int[] chrRom = new int[EIGHT_KB];
        MockMapper mapper = new MockMapper();

        // Fill PRG ROM with a pattern to verify reading
        for (int i = 0; i < prgRom.length; i++) {
            prgRom[i] = i & 0xFF;
        }

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        // Test read from first unit
        mapper.mappedPrgAddress = 0x55;
        assertEquals(0x55, cartridge.cpuRead(0x8000));

        // Test read from second unit
        mapper.mappedPrgAddress = SIXTEEN_KB + 0x55;
        assertEquals(0x55, cartridge.cpuRead(0x8000));
    }

    @Test
    void testMapperAddressBoundaries() {
        int[] prgRom = new int[SIXTEEN_KB];
        int[] chrRom = new int[EIGHT_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        mapper.mappedPrgAddress = 0;
        assertEquals(0, cartridge.cpuRead(0x8000));

        mapper.mappedPrgAddress = SIXTEEN_KB - 1;
        assertEquals(0, cartridge.cpuRead(0x8000));

        // Test boundary of CPU address range for PRG ROM
        assertEquals(0, cartridge.cpuRead(0x7FFF)); // Just below PRG ROM range
        assertEquals(0, cartridge.cpuRead(0x8000)); // Start of PRG ROM range
        assertEquals(0, cartridge.cpuRead(0xFFFF)); // End of PRG ROM range
    }

    @Test
    void testPpuWriteOutOfBounds() {
        int[] prgRom = new int[SIXTEEN_KB];
        int[] chrRom = new int[0];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                chrRom,
                mapper,
                false,
                MirroringMode.HORIZONTAL);

        // Set the mapper to return an out-of-bounds address
        mapper.mappedChrAddress = EIGHT_KB + 100;

        // This write should not crash even though the address is out of bounds
        cartridge.ppuWrite(0x1000, 0x42);

        // Verify nothing was written (read should return 0)
        assertEquals(0, cartridge.ppuRead(0x1000));
    }

    @Test
    void testLoadIncompleteSaveData() {
        int[] prgRom = new int[SIXTEEN_KB];
        MockMapper mapper = new MockMapper();

        Cartridge cartridge = new Cartridge(
                prgRom,
                new int[EIGHT_KB],
                mapper,
                true,
                MirroringMode.HORIZONTAL);

        // Write initial value
        cartridge.cpuWrite(0x6000, 0x42);

        // Create incomplete save data (smaller than PRG RAM)
        byte[] incompleteSaveData = new byte[EIGHT_KB / 2];
        Arrays.fill(incompleteSaveData, (byte) 0xFF);

        // Load incomplete data - implementation should reject it
        cartridge.loadSaveData(incompleteSaveData);

        // Original value should remain unchanged
        assertEquals(0x42, cartridge.cpuRead(0x6000));
    }

//    @Test
//    void testFromNesFileWithDifferentMappers() {
//        // Test NROM (mapper 0)
//        byte[] nromRom = createMockNesRom(1, 1, 0x00, false, false, false, MirroringMode.HORIZONTAL);
//        Cartridge nromCartridge = Cartridge.fromNesFile(nromRom);
//        assertEquals(0, nromCartridge.getMapperId());
//
//        // Test MMC1 (mapper 1) - set bits 4-7 of flags6 and bits 4-7 of flags7
//        byte[] mmc1Rom = createMockNesRom(1, 1, 0x00, false, false, false, MirroringMode.HORIZONTAL);
//        mmc1Rom[6] = (byte)(mmc1Rom[6] | 0x10); // Set bit 4 of flags6
//        Cartridge mmc1Cartridge = Cartridge.fromNesFile(mmc1Rom);
//        assertEquals(1, mmc1Cartridge.getMapperId());
//
//        // Test UxROM (mapper 2)
//        byte[] uxromRom = createMockNesRom(1, 1, 0x00, false, false, false, MirroringMode.HORIZONTAL);
//        uxromRom[6] = (byte)(uxromRom[6] | 0x20); // Set bit 5 of flags6
//        Cartridge uxromCartridge = Cartridge.fromNesFile(uxromRom);
//        assertEquals(2, uxromCartridge.getMapperId());
//    }

    private int[] createPrgRomWithPattern(int size) {
        int[] rom = new int[size];
        for (int i = 0; i < size; i++) {
            rom[i] = i & 0xFF;
        }
        return rom;
    }

    private int[] createChrRomWithPattern(int size) {
        int[] rom = new int[size];
        for (int i = 0; i < size; i++) {
            rom[i] = i & 0xFF;
        }
        return rom;
    }

    private byte[] createMockNesRom(int prgSize, int chrSize, int flags6, boolean batteryBacked,
                                    boolean hasTrainer, boolean fourScreenVram, MirroringMode mirroringMode) {
        if (batteryBacked) flags6 |= 0x02;
        if (hasTrainer) flags6 |= 0x04;
        if (fourScreenVram) flags6 |= 0x08;
        if (mirroringMode == MirroringMode.VERTICAL) flags6 |= 0x01;

        int trainerSize = hasTrainer ? 512 : 0;
        int prgRomSize = prgSize * SIXTEEN_KB;
        int chrRomSize = chrSize * EIGHT_KB;

        int fileSize = 16 + trainerSize + prgRomSize + chrRomSize;
        byte[] rom = new byte[fileSize];

        rom[0] = 'N';
        rom[1] = 'E';
        rom[2] = 'S';
        rom[3] = 0x1A;
        rom[4] = (byte)prgSize;
        rom[5] = (byte)chrSize;
        rom[6] = (byte)flags6;
        rom[7] = (byte)0;

        int prgOffset = 16 + trainerSize;
        for (int i = 0; i < prgRomSize; i++) {
            rom[prgOffset + i] = (byte)(i & 0xFF);
        }

        if (chrSize > 0) {
            int chrOffset = prgOffset + prgRomSize;
            for (int i = 0; i < chrRomSize; i++) {
                rom[chrOffset + i] = (byte)(i & 0xFF);
            }
        }

        return rom;
    }
}