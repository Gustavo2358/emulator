import mapper.NROMMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NROMMapperTest {

    public static final int SIXTEEN_KB = 16384;
    public static final int THIRTY_TWO_KB = 32768;

    @Test
    void mapPrgRomAddressFor16kbMapsLowerBank() {
        NROMMapper mapper = new NROMMapper(SIXTEEN_KB);
        int result = mapper.mapPrgRomAddress(THIRTY_TWO_KB);
        assertEquals(0, result);
    }

    @Test
    void mapPrgRomAddressFor16kbMapsUpperBankAsMirror() {
        NROMMapper mapper = new NROMMapper(SIXTEEN_KB);
        int result = mapper.mapPrgRomAddress(0xC000);
        assertEquals(0, result);
    }

    @Test
    void mapPrgRomAddressFor32kbMapsLowerBank() {
        NROMMapper mapper = new NROMMapper(THIRTY_TWO_KB);
        int result = mapper.mapPrgRomAddress(THIRTY_TWO_KB);
        assertEquals(0, result);
    }

    @Test
    void mapPrgRomAddressFor32kbMapsUpperBank() {
        NROMMapper mapper = new NROMMapper(THIRTY_TWO_KB);
        int result = mapper.mapPrgRomAddress(0xC000);
        assertEquals(SIXTEEN_KB, result);
    }

    @Test
    void mapPrgRomAddressThrowsForUnsupportedSize() {
        NROMMapper mapper = new NROMMapper(24576);
        assertThrows(IllegalStateException.class, () -> mapper.mapPrgRomAddress(THIRTY_TWO_KB));
    }

    @Test
    void mapChrRomAddressReturnsSameAddress() {
        NROMMapper mapper = new NROMMapper(SIXTEEN_KB);
        assertEquals(123, mapper.mapChrRomAddress(123));
    }

    @Test
    void getIdReturnsZero() {
        NROMMapper mapper = new NROMMapper(SIXTEEN_KB);
        assertEquals(0, mapper.getId());
    }

    @Test
    void mapPrgRomAddressFor16kbAtLowerBankBoundary() {
        NROMMapper mapper = new NROMMapper(SIXTEEN_KB);
        int result = mapper.mapPrgRomAddress(0xBFFF);
        assertEquals(0x3FFF, result); // 0xBFFF - 0x8000 = 0x3FFF (last byte of lower bank)
    }

    @Test
    void mapPrgRomAddressFor16kbAtUpperBankBoundary() {
        NROMMapper mapper = new NROMMapper(SIXTEEN_KB);
        int result = mapper.mapPrgRomAddress(0xFFFF);
        assertEquals(0x3FFF, result); // Mirrors to the last byte of the 16KB ROM
    }

    @Test
    void mapPrgRomAddressFor32kbAtBankBoundary() {
        NROMMapper mapper = new NROMMapper(THIRTY_TWO_KB);
        int result = mapper.mapPrgRomAddress(0xBFFF);
        assertEquals(0x3FFF, result); // 0xBFFF - 0x8000 = 0x3FFF

        int result2 = mapper.mapPrgRomAddress(0xC000);
        assertEquals(0x4000, result2); // 0xC000 - 0x8000 = 0x4000
    }

    @Test
    void mapPrgRomAddressFor16kbMiddleOfUpperBank() {
        NROMMapper mapper = new NROMMapper(SIXTEEN_KB);
        int result = mapper.mapPrgRomAddress(0xDFFF); // From upper bank
        assertEquals(0x1FFF, result); // Should map to middle of the 16KB ROM (0xDFFF - 0xC000 = 0x1FFF)
    }

    @Test
    void mapPrgRomAddressThrowsForOutOfRangeAddress() {
        NROMMapper mapper = new NROMMapper(SIXTEEN_KB);
        assertThrows(IllegalArgumentException.class, () -> mapper.mapPrgRomAddress(0x7FFF)); // Below valid range
        assertThrows(IllegalArgumentException.class, () -> mapper.mapPrgRomAddress(0x10000)); // Above valid range
    }
}