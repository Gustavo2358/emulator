public class MapperFactory {
    public static Mapper createMapper(int mapperId, int prgRomSize, int chrRomSize) {
        switch (mapperId) {
            case 0:
                return new NROMMapper(prgRomSize);
            default:
                throw new IllegalArgumentException("Unsupported mapper number: " + mapperId);
        }
    }
}
