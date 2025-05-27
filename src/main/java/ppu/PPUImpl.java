package ppu;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

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

    // ---------------------------------------------------------------------
    public PPUImpl() {
        vram = new VRAM();
        oam = new OAM();
        paletteRam = new PaletteRam();

        frameBuffer = new BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB);
        frameData = ((DataBufferInt) frameBuffer.getRaster().getDataBuffer()).getData();

        reset();
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
    private int ppuRead(int address) {
        address &= 0x3FFF;

        if (address <= 0x1FFF) {
            return vram.read(address);
        } else if (address <= 0x3EFF) {
            int mirroredAddr = mirrorAddress(address);
            return vram.read(mirroredAddr);
        } else if (address == 0x3F00) {
            return 0x10;
        } else if ((address & 0x03) == 0) {
            address &= 0x0010;
        }
        return paletteRam.read(address);
    }

    private void ppuWrite(int address, int value) {
        address &= 0x3FFF;

        if (address <= 0x1FFF) {
            vram.write(address, value);
        } else if (address <= 0x3EFF) {
            int mirroredAddr = mirrorAddress(address);
            vram.write(mirroredAddr, value);
        } else if (address == 0x3F00) {
            address = 0x0010;
        } else if ((address & 0x03) == 0) {
            address &= 0x0010;
        }
        paletteRam.write(address, value);
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
        if (scanline <= 261) {
            if (cycle == 1) {
                ppuStatus &= ~0xE0;
            }
            if (cycle >= 280 && cycle <= 304) {
                if ((ppuMask & 0x18) != 0) {
                    vRamAddr = (vRamAddr & 0x7BE0) | (tRamAddr & 0x7BE0);
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
                            bgNextTileAttribute = ((ppuRead(attributeAddress) >> shift) & 0x3) << 2;
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
                }
                if (cycle == 257) {
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
                    boolean spriteZeroNext = false;

                    while (oamIndex < 64 && spriteCount < 8) {
                        int y = oam.read(oamIndex * 4);
                        int nextScanln = scanline + 1;
                        if (nextScanln >= y && nextScanln < (y + ((ppuCtrl & 0x20) == 0x20 ? 16 : 8))) {
                            if (oamIndex == 0) {
                                spriteZeroNext = true;
                            }

                            spriteY[spriteCount] = y;
                            spriteTile[spriteCount] = oam.read(oamIndex * 4 + 1);
                            spriteAttribute[spriteCount] = oam.read(oamIndex * 4 + 2);
                            spriteX[spriteCount] = oam.read(oamIndex * 4 + 3);

                            int tileAddr;
                            int row = nextScanln - y;
                            if ((spriteAttribute[spriteCount] & 0x80) == 0x80) {
                                row = 7 - row;
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

                if (cycle >= 1 && cycle <= 256 && scanline >= 0 && scanline < 240) {
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
                                    fpriority = (spriteAttribute[i] & 0x20) > 0 ? 1 : 0;

                                    if (fp != 0) {
                                        if (i == 0 && bgPixel != 0 && cycle != 256) {
                                            ppuStatus |= 0x40;
                                        }
                                        break;
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

                    int pixel = 0;
                    int palette = 0;

                    if (bgPixel == 0 && fpixel == 0) {
                        pixel = 0;
                        palette = 0;
                    } else if (bgPixel == 0 && fpixel > 0) {
                        pixel = fpixel;
                        palette = fpalette;
                    } else if (bgPixel > 0 && fpixel == 0) {
                        pixel = bgPixel;
                        palette = bgPalette;
                    } else {
                        if (fpriority == 1) {
                            pixel = fpixel;
                            palette = fpalette;
                        } else {
                            pixel = bgPixel;
                            palette = bgPalette;
                        }
                    }

                    int colorAddr = 0x3F00 | (palette << 2) | pixel;
                    int colorIndex = ppuRead(colorAddr) & 0x3F;

                    int pixelIndex = (scanline * 256) + (cycle - 1);
                    if (pixelIndex >= 0 && pixelIndex < frameData.length) {
                        frameData[pixelIndex] = PALETTE[colorIndex];
                    }
                }
            }
        }

        if (scanline == 241 && cycle == 1) {
            ppuStatus |= 0x80;
            if ((ppuCtrl & 0x80) != 0) {
                nmiOccurred = true;
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
