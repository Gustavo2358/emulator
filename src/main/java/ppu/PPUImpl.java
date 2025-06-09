package ppu;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import core.CPU;
import core.Cartridge; // Added import
import core.Bus; // Added import for cpuBus


public class PPUImpl implements PPU {
    // PPU registers ---------------------------------------------------------
    private int ppuCtrl;   // $2000 – PPUCTRL
    private int ppuMask;   // $2001 – PPUMASK
    private int ppuStatus; // $2002 – PPUSTATUS
    private int oamAddr;   // $2003 – OAMADDR
    private int ppuScroll; // $2005 – PPUSCROLL
    private int ppuAddr;   // $2006 – PPUADDR
    private int ppuData;   // $2007 – PPUDATA

    // Loopy registers / internal latch state ------------------------------
    private int vRamAddr;     // Current VRAM address  (15 bits)
    private int tRamAddr;     // Temporary VRAM address (15 bits)
    private int fineX;        // Fine‑X scroll (3 bits)
    private int addressLatch; // First/second write toggle
    private int dataBuffer;   // PPUDATA read buffer

    // Scanline / cycle counters -------------------------------------------
    private int scanline; // 0‑261
    private int cycle;    // 0‑340
    private boolean oddFrame; // NTSC skip‑cycle flag

    // NMI logic ------------------------------------------------------------
    private boolean nmiOccurred;
    private boolean nmiOutput;
    private boolean nmiPrevious;

    // External memory ------------------------------------------------------
    private final VRAM vram;
    private final OAM oam;
    private final PaletteRam paletteRam;
    private final Cartridge cartridge; // Added core.Cartridge field
    private CPU cpu; // Added core.CPU field (not final, to be set by setter)

    // Frame buffer ---------------------------------------------------------
    private final BufferedImage frameBuffer;
    private final int[] frameData;

    // NES master palette ---------------------------------------------------
    private static final int[] PALETTE = {
            0x7C7C7C, 0x0000FC, 0x0000BC, 0x4428BC, 0x940084, 0xA80020, 0xA81000, 0x881400,
            0x503000, 0x006800, 0x005800, 0x004058, 0x000000, 0x000000, 0x000000, 0x000000,
            0xBCBCBC, 0x0078F8, 0x0058F8, 0x6844FC, 0xD800CC, 0xE40058, 0xF83800, 0xE45C10,
            0xAC7C00, 0x00B800, 0x00A800, 0x00A844, 0x008888, 0x000000, 0x000000, 0x000000,
            0xF8F8F8, 0x3CBCFC, 0x6888FC, 0x9878F8, 0xF878F8, 0xF85898, 0xF87858, 0xFCA044,
            0xF8B800, 0xB8F818, 0x58D854, 0x58F898, 0x00E8D8, 0x787878, 0x000000, 0x000000,
            0xFCFCFC, 0xA4E4FC, 0xB8B8F8, 0xD8B8F8, 0xF8B8F8, 0xF8A4C0, 0xF0D0B0, 0xFCE0A8,
            0xF8D878, 0xD8F878, 0xB8F8B8, 0xB8F8D8, 0x00FCFC, 0xF8D8F8, 0x000000, 0x000000
    };

    // Secondary OAM --------------------------------------------------------
    private final int[] spriteX = new int[8];
    private final int[] spriteY = new int[8];
    private final int[] spriteTile = new int[8];
    private final int[] spriteAttribute = new int[8];
    private final int[] spriteDataLow = new int[8];
    private final int[] spriteDataHigh = new int[8];
    private int spriteCount;

    // Background fetching latches -----------------------------------------
    private int bgNextTileId;
    private int bgNextTileAttribute;
    private int bgNextTileLow;
    private int bgNextTileHigh;
    private int bgShifterPatternLow;
    private int bgShifterPatternHigh;
    private int bgShifterAttributeLow;
    private int bgShifterAttributeHigh;

    // OAMDMA related fields
    private Bus cpuBus; // To access CPU memory during DMA
    private boolean oamDmaActive = false;
    private int oamDmaPageForTransfer; // Stores the page for the current DMA
    private int oamDmaCyclesRemaining; // PPU cycles PPU is "busy" or managing DMA

    // ---------------------------------------------------------------------
    public PPUImpl(Cartridge cartridge) { // Modified constructor
        this.vram = new VRAM();
        this.oam = new OAM();
        this.paletteRam = new PaletteRam();
        this.cartridge = cartridge; // Initialize cartridge
        // this.cpu will be set via setter

        frameBuffer = new BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB);
        frameData = ((DataBufferInt) frameBuffer.getRaster().getDataBuffer()).getData();

        reset();
    }

    public void setCpu(CPU cpu) { // Added setter for core.CPU
        this.cpu = cpu;
    }

    @Override
    public void setCpuBus(Bus bus) {
        this.cpuBus = bus;
    }

    @Override
    public void startOAMDMA(int page) {
        if (this.cpuBus == null) {
            System.err.println("PPU Error: CPU Bus not set. Cannot perform OAMDMA.");
            return;
        }

        this.oamDmaPageForTransfer = page & 0xFF; // Ensure page is a byte
        this.oamDmaActive = true;
        this.oamDmaCyclesRemaining = 514; // PPU is "busy" for this duration

        // Perform the 256-byte copy from CPU RAM to PPU OAM immediately
        int startAddressInCpuRam = this.oamDmaPageForTransfer << 8;
        for (int i = 0; i < 256; i++) {
            int dataByte = this.cpuBus.read(startAddressInCpuRam + i);
            this.oam.write(i, dataByte); // Assuming oam.write(index, value) correctly writes to the OAM array
        }

        // Signal the CPU to stall
        if (this.cpu != null) {
            this.cpu.stallForDMA(514); // Request CPU to stall for 514 CPU cycles
        }
    }

    // =====================================================================
    // Public API
    // =====================================================================
    @Override
    public void reset() {
        ppuCtrl = 0;
        ppuMask = 0;
        ppuStatus = 0;
        oamAddr = 0;
        ppuScroll = 0;
        ppuAddr = 0;
        ppuData = 0;

        vRamAddr = 0;
        tRamAddr = 0;
        fineX = 0;
        addressLatch = 0;
        dataBuffer = 0;

        scanline = 0;
        cycle = 0;
        oddFrame = false;

        nmiOccurred = false;
        nmiOutput = false;
        nmiPrevious = false;

        for (int i = 0; i < 8; i++) {
            spriteX[i] = 0xFF;
            spriteY[i] = 0xFF;
            spriteTile[i] = 0xFF;
            spriteAttribute[i] = 0xFF;
            spriteDataLow[i] = 0;
            spriteDataHigh[i] = 0;
        }
        spriteCount = 0;

        bgNextTileId = 0;
        bgNextTileAttribute = 0;
        bgNextTileLow = 0;
        bgNextTileHigh = 0;
        bgShifterPatternLow = 0;
        bgShifterPatternHigh = 0;
        bgShifterAttributeLow = 0;
        bgShifterAttributeHigh = 0;
    }

    @Override
    public int read(int address) {
        int mappedAddr = address & 0x7;

        if (address < 0x2000 || address > 0x3FFF) {
            return 0;
        }

        switch (mappedAddr) {
            case 0x2: { // PPUSTATUS
                int result = (ppuStatus & 0xE0) | (dataBuffer & 0x1F);
                ppuStatus &= ~0x80;
                addressLatch = 0;
                nmiOccurred = false;
                return result;
            }
            case 0x4: // OAMDATA
                return oam.read(oamAddr);
            case 0x7: { // PPUDATA
                int data;
                if (vRamAddr <= 0x3EFF) {
                    data = dataBuffer;
                    dataBuffer = ppuRead(vRamAddr);
                } else {
                    data = ppuRead(vRamAddr);
                }
                vRamAddr += ((ppuCtrl & 0x04) == 0x04) ? 32 : 1;
                return data;
            }
            default:
                return dataBuffer;
        }
    }

    @Override
    public void write(int address, int value) {
        int mappedAddr = address & 0x7;

        if (address < 0x2000 || address > 0x3FFF) {
            return;
        }

        switch (mappedAddr) {
            case 0x0: // PPUCTRL
                ppuCtrl = value;
                tRamAddr = (tRamAddr & 0xF3FF) | ((value & 0x03) << 10);
                nmiOutput = (value & 0x80) == 0x80;
                break;
            case 0x1: // PPUMASK
                ppuMask = value;
                break;
            case 0x3: // OAMADDR
                oamAddr = value;
                break;
            case 0x4: // OAMDATA
                oam.write(oamAddr, value);
                oamAddr = (oamAddr + 1) & 0xFF;
                break;
            case 0x5: // PPUSCROLL
                if (addressLatch == 0) {
                    fineX = value & 0x07;
                    tRamAddr = (tRamAddr & 0x7FE0) | (value >> 3);
                    addressLatch = 1;
                } else {
                    tRamAddr = (tRamAddr & 0x8FFF) | ((value & 0x07) << 12);
                    tRamAddr = (tRamAddr & 0xFC1F) | ((value >> 3) << 5);
                    addressLatch = 0;
                }
                break;
            case 0x6: // PPUADDR
                if (addressLatch == 0) {
                    tRamAddr = (tRamAddr & 0x00FF) | ((value & 0x3F) << 8);
                    addressLatch = 1;
                } else {
                    tRamAddr = (tRamAddr & 0xFF00) | value;
                    vRamAddr = tRamAddr;
                    addressLatch = 0;
                }
                break;
            case 0x7: // PPUDATA
                ppuWrite(vRamAddr, value);
                vRamAddr += ((ppuCtrl & 0x04) == 0x04) ? 32 : 1;
                break;
        }
    }

    // =====================================================================
    // Internal PPU read/write helpers
    // =====================================================================
    private int ppuRead(int address) { // PPU bus address 0x0000 - 0x3FFF
        address &= 0x3FFF;
        if (address <= 0x1FFF) { // Pattern Tables ($0000-$1FFF)
            return this.cartridge.ppuRead(address);
        } else if (address >= 0x2000 && address <= 0x3EFF) { // Nametable region ($2000-$3EFF, mirrors $3000-$3EFF are to $2000-$2EFF)
            int vramIndex = 0;
            // Normalize address to $2000-$2FFF range first for logical table determination
            int normalizedAddress = 0x2000 | (address & 0x0FFF);
            int logicalTable = (normalizedAddress >> 10) & 3; // 0, 1, 2, or 3
            int offsetInTable = normalizedAddress & 0x03FF;   // Offset within a 1KB nametable

            switch (cartridge.getMirroringMode()) {
                case HORIZONTAL: // Tables 0 & 1 map to VRAM NT0; Tables 2 & 3 map to VRAM NT1
                    if (logicalTable == 0 || logicalTable == 1) {
                        vramIndex = offsetInTable; // Maps to physical VRAM NT0 (0x0000-0x03FF)
                    } else { // logicalTable == 2 || logicalTable == 3
                        vramIndex = offsetInTable + 0x0400; // Maps to physical VRAM NT1 (0x0400-0x07FF)
                    }
                    break;
                case VERTICAL:   // Tables 0 & 2 map to VRAM NT0; Tables 1 & 3 map to VRAM NT1
                    if (logicalTable == 0 || logicalTable == 2) {
                        vramIndex = offsetInTable; // Maps to physical VRAM NT0
                    } else { // logicalTable == 1 || logicalTable == 3
                        vramIndex = offsetInTable + 0x0400; // Maps to physical VRAM NT1
                    }
                    break;
                case FOUR_SCREEN:
                    vramIndex = normalizedAddress & 0x07FF; // Basic mapping for 2KB if no cart VRAM for 4-screen
                    break;
                default: // Should not happen with valid MirroringMode enum
                    vramIndex = normalizedAddress & 0x07FF;
            }
            return vram.read(vramIndex); // vram.read expects 0x000-0x7FF index
        } else if (address >= 0x3F00 && address <= 0x3FFF) { // Palette RAM ($3F00-$3FFF)
            int paletteIndex = address & 0x1F;
            // Palette Mirrors: $3F10, $3F14, $3F18, $3F1C mirror $3F00, $3F04, $3F08, $3F0C
            if (paletteIndex == 0x10 || paletteIndex == 0x14 || paletteIndex == 0x18 || paletteIndex == 0x1C) {
                paletteIndex -= 0x10;
            }
            return paletteRam.read(paletteIndex) & 0x3F; // NES palette colors are 6-bit
        }
        return 0; // Should be unreachable if PPU address space is fully handled
    }

    private void ppuWrite(int address, int value) { // PPU bus address 0x0000 - 0x3FFF
        address &= 0x3FFF;
        if (address <= 0x1FFF) { // Pattern Tables
            this.cartridge.ppuWrite(address, value);
        } else if (address >= 0x2000 && address <= 0x3EFF) { // Nametable region
            int vramIndex = 0;
            int normalizedAddress = 0x2000 | (address & 0x0FFF);
            int logicalTable = (normalizedAddress >> 10) & 3;
            int offsetInTable = normalizedAddress & 0x03FF;

            switch (cartridge.getMirroringMode()) {
                case HORIZONTAL:
                    if (logicalTable == 0 || logicalTable == 1) vramIndex = offsetInTable;
                    else vramIndex = offsetInTable + 0x0400;
                    break;
                case VERTICAL:
                    if (logicalTable == 0 || logicalTable == 2) vramIndex = offsetInTable;
                    else vramIndex = offsetInTable + 0x0400;
                    break;
                case FOUR_SCREEN:
                    vramIndex = normalizedAddress & 0x07FF; // Simplified for 2KB internal VRAM
                    break;
                default:
                    vramIndex = normalizedAddress & 0x07FF;
            }
            vram.write(vramIndex, value);
        } else if (address >= 0x3F00 && address <= 0x3FFF) { // Palette RAM
            int paletteIndex = address & 0x1F;
            if (paletteIndex == 0x10 || paletteIndex == 0x14 || paletteIndex == 0x18 || paletteIndex == 0x1C) {
                paletteIndex -= 0x10;
            }
            paletteRam.write(paletteIndex, value);
        }
    }

    private int mirrorAddress(int address) {
        address &= 0x2FFF;
        int table = (address >> 10) & 0x03;
        int offset = address & 0x03FF;

        table = (table & 0x01) | ((table & 0x02) >> 1);
        return 0x2000 | (table << 10) | offset;
    }

    // =====================================================================
    // Rendering – one PPU cycle -------------------------------------------
    // =====================================================================
    @Override
    public void runCycle() {
        boolean wasDmaActiveThisCycleStart = oamDmaActive;

        if (oamDmaActive) {
            oamDmaCyclesRemaining--;
            if (oamDmaCyclesRemaining <= 0) {
                oamDmaActive = false;
            }
        }

        if (!wasDmaActiveThisCycleStart) {
            if (scanline <= 261) {
                if (cycle == 1) {
                    ppuStatus &= ~0xE0;
                    nmiOccurred = false;
                }
                if (scanline == 261 && cycle >= 280 && cycle <= 304) {
                    if ((ppuMask & 0x18) != 0) {
                        vRamAddr = (vRamAddr & 0x041F) | (tRamAddr & 0x7BE0);
                    }
                }

                if (scanline < 240) {
                    if ((cycle >= 1 && cycle <= 256) || (cycle >= 321 && cycle <= 336)) {
                        updateShifters();

                        switch ((cycle - 1) % 8) {
                            case 0:
                                loadBackgroundShifters();
                                bgNextTileId = ppuRead(0x2000 | (vRamAddr & 0x0FFF));
                                break;
                            case 2: {
                                int attributeAddress = 0x23C0 | (vRamAddr & 0x0C00) | ((vRamAddr >> 4) & 0x38) | ((vRamAddr >> 2) & 0x07);
                                int shift = ((vRamAddr >> 4) & 4) | (vRamAddr & 2);
                                bgNextTileAttribute = (ppuRead(attributeAddress) >> shift) & 0x3;
                                break;
                            }
                            case 4: {
                                int patternAddress = ((ppuCtrl & 0x10) << 8) | (bgNextTileId << 4) | ((vRamAddr >> 12) & 7);
                                bgNextTileLow = ppuRead(patternAddress);
                                break;
                            }
                            case 6: {
                                int patternAddress = ((ppuCtrl & 0x10) << 8) | (bgNextTileId << 4) | ((vRamAddr >> 12) & 7) | 8;
                                bgNextTileHigh = ppuRead(patternAddress);
                                break;
                            }
                            case 7:
                                incrementX();
                                break;
                        }
                    }

                    if (cycle == 256) {
                        incrementY();
                    }
                    if (cycle == 257) {
                        loadBackgroundShifters();
                        if ((ppuMask & 0x18) != 0) {
                            vRamAddr = (vRamAddr & ~0x041F) | (tRamAddr & 0x041F);
                        }

                        for (int i = 0; i < 8; i++) {
                            spriteX[i] = 0xFF;
                            spriteY[i] = 0xFF;
                            spriteTile[i] = 0xFF;
                            spriteAttribute[i] = 0xFF;
                            spriteDataLow[i] = 0;
                            spriteDataHigh[i] = 0;
                        }
                        spriteCount = 0;
                        int oamIndex = 0;

                        while (oamIndex < 64 && spriteCount < 8) {
                            int y = oam.read(oamIndex * 4);
                            int nextScanln = scanline + 1; // Sprite evaluation is for the *next* scanline
                            int spriteHeight = ((ppuCtrl & 0x20) == 0x20 ? 16 : 8);
                            if (nextScanln >= y && nextScanln < (y + spriteHeight)) {
                                spriteY[spriteCount] = y;
                                spriteTile[spriteCount] = oam.read(oamIndex * 4 + 1);
                                spriteAttribute[spriteCount] = oam.read(oamIndex * 4 + 2);
                                spriteX[spriteCount] = oam.read(oamIndex * 4 + 3);

                                int tileAddr;
                                int row = nextScanln - y;
                                if ((spriteAttribute[spriteCount] & 0x80) == 0x80) {
                                    row = spriteHeight - 1 - row;
                                }

                                if ((ppuCtrl & 0x20) == 0) {
                                    tileAddr = ((ppuCtrl & 0x08) << 9) | (spriteTile[spriteCount] << 4) | row;
                                } else {
                                    tileAddr = ((spriteTile[spriteCount] & 0x01) << 12) | ((spriteTile[spriteCount] & 0xFE) << 4) | row;
                                }

                                spriteDataLow[spriteCount] = ppuRead(tileAddr);
                                spriteDataHigh[spriteCount] = ppuRead(tileAddr | 8);

                                if ((spriteAttribute[spriteCount] & 0x40) == 0x40) {
                                    spriteDataLow[spriteCount] = reverseBits(spriteDataLow[spriteCount]);
                                    spriteDataHigh[spriteCount] = reverseBits(spriteDataHigh[spriteCount]);
                                }

                                spriteCount++;
                            }
                            oamIndex++;
                        }

                        if (oamIndex >= 64 && spriteCount >= 8) {
                            ppuStatus |= 0x20;
                        }
                    }

                    if (cycle >= 1 && cycle <= 256 && scanline >= 0) {
                        renderPixelForCurrentPosition();
                    }
                }
            }

            if (scanline == 241 && cycle == 1) {
                ppuStatus |= 0x80; // Set VBlank flag
                if ((ppuCtrl & 0x80) != 0) { // If NMI is enabled in PPUCTRL
                    this.nmiOccurred = true; // PPU's internal flag that NMI condition happened
                    if (this.cpu != null) {
                        this.cpu.triggerNMI(); // Signal the core.CPU
                    }
                }
            }
        }

        cycle++;
        if (cycle > 340) {
            cycle = 0;
            scanline++;
            if (scanline > 261) {
                scanline = 0;
                oddFrame = !oddFrame;

                if (oddFrame && (ppuMask & 0x08) != 0) {
                    cycle = 1;
                }
            }
        }

        boolean nmi = nmiOutput && nmiOccurred;
        if (nmi && !nmiPrevious) {
            nmiPrevious = true;
        } else if (!nmi && nmiPrevious) {
            nmiPrevious = false;
        }
    }

    private void renderPixelForCurrentPosition() {
        int bgPixel = 0;
        int bgPalette = 0;

        if ((ppuMask & 0x08) != 0) {
            if ((cycle % 8) != 0 || (ppuMask & 0x02) != 0) {
                int bitMux = 0x8000 >> fineX;

                int p0 = (bgShifterPatternLow & bitMux) > 0 ? 1 : 0;
                int p1 = (bgShifterPatternHigh & bitMux) > 0 ? 1 : 0;
                bgPixel = p0 | (p1 << 1);

                int pal0 = (bgShifterAttributeLow & bitMux) > 0 ? 1 : 0;
                int pal1 = (bgShifterAttributeHigh & bitMux) > 0 ? 1 : 0;
                bgPalette = pal0 | (pal1 << 1);
            }
        }

        int fpixel = 0;
        int fpalette = 0;
        int fpriority = 0;

        if ((ppuMask & 0x10) != 0) {
            if ((cycle % 8) != 0 || (ppuMask & 0x04) != 0) {
                for (int i = 0; i < spriteCount; i++) {
                    if (spriteX[i] == 0) {
                        int fp = ((spriteDataLow[i] & 0x80) > 0 ? 1 : 0);
                        fp |= ((spriteDataHigh[i] & 0x80) > 0 ? 2 : 0);
                        fpalette = (spriteAttribute[i] & 0x03) + 4;
                        fpriority = (spriteAttribute[i] & 0x20) > 0 ? 1 : 0; // 1 if sprite behind BG, 0 if in front

                        if (fp != 0) { // If sprite pixel is not transparent
                            fpixel = fp; // Assign the calculated sprite pixel to fpixel
                            if (i == 0 && bgPixel != 0 && cycle != 256) { // Sprite 0 hit detection
                                ppuStatus |= 0x40;
                            }
                            break; // Found first opaque sprite pixel for this X
                        }
                    }
                }

                for (int i = 0; i < spriteCount; i++) {
                    if (spriteX[i] > 0) {
                        spriteX[i]--;
                    } else {
                        spriteDataLow[i] <<= 1;
                        spriteDataHigh[i] <<= 1;
                    }
                }
            }
        }

        int pixel;
        int palette;

        boolean bgIsTransparent = (bgPixel == 0);
        boolean spriteIsTransparent = (fpixel == 0);

        if (bgIsTransparent && spriteIsTransparent) {
            // Both background and sprite are transparent
            pixel = 0;
            palette = 0;
        } else if (bgIsTransparent && !spriteIsTransparent) {
            // Background is transparent, sprite is visible
            pixel = fpixel;
            palette = fpalette;
        } else if (!bgIsTransparent && spriteIsTransparent) {
            // Background is visible, sprite is transparent
            pixel = bgPixel;
            palette = bgPalette;
        } else {
            // Both background and sprite are visible, apply priority
            if (fpriority == 1) { // Sprite priority bit 5 is set (1 means behind background)
                pixel = bgPixel;
                palette = bgPalette;
            } else { // Sprite priority bit 5 is clear (0 means in front of background)
                pixel = fpixel;
                palette = fpalette;
            }
        }

        int colorAddr = 0x3F00 | (palette << 2) | pixel;
        int colorIndex = ppuRead(colorAddr) & 0x3F;

        int pixelIndex = (scanline * 256) + (cycle - 1);
        if (pixelIndex >= 0 && pixelIndex < frameData.length) {
            frameData[pixelIndex] = PALETTE[colorIndex];
        }
    }

    // =====================================================================
    // Helper methods -------------------------------------------------------
    // =====================================================================
    private void updateShifters() {
        if ((ppuMask & 0x08) != 0) {
            bgShifterPatternLow <<= 1;
            bgShifterPatternHigh <<= 1;
            bgShifterAttributeLow <<= 1;
            bgShifterAttributeHigh <<= 1;
        }
    }

    private void loadBackgroundShifters() {
        bgShifterPatternLow = (bgShifterPatternLow & 0xFF00) | bgNextTileLow;
        bgShifterPatternHigh = (bgShifterPatternHigh & 0xFF00) | bgNextTileHigh;

        bgShifterAttributeLow = (bgShifterAttributeLow & 0xFF00) | ((bgNextTileAttribute & 0x01) != 0 ? 0xFF : 0x00);
        bgShifterAttributeHigh = (bgShifterAttributeHigh & 0xFF00) | ((bgNextTileAttribute & 0x02) != 0 ? 0xFF : 0x00);
    }

    private void incrementX() {
        if ((ppuMask & 0x08) == 0) {
            return;
        }
        if ((vRamAddr & 0x001F) == 31) {
            vRamAddr &= ~0x001F;
            vRamAddr ^= 0x0400;
        } else {
            vRamAddr++;
        }
    }

    private void incrementY() {
        if ((ppuMask & 0x08) == 0) {
            return;
        }
        if ((vRamAddr & 0x7000) != 0x7000) {
            vRamAddr += 0x1000;
        } else {
            vRamAddr &= ~0x7000;
            int y = (vRamAddr & 0x03E0) >> 5;
            if (y == 29) {
                y = 0;
                vRamAddr ^= 0x0800;
            } else if (y == 31) {
                y = 0;
            } else {
                y++;
            }
            vRamAddr = (vRamAddr & ~0x03E0) | (y << 5);
        }
    }

    private int reverseBits(int b) {
        b = (b & 0xF0) >> 4 | (b & 0x0F) << 4;
        b = (b & 0xCC) >> 2 | (b & 0x33) << 2;
        b = (b & 0xAA) >> 1 | (b & 0x55) << 1;
        return b;
    }

    public BufferedImage getFrameBuffer() {
        return frameBuffer;
    }
}
