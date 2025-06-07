package core;

import ppu.PPU;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;
import java.io.File;

public class EmulatorUI extends JFrame {
    private static final int SCALE = 2;
    private static final int WIDTH = 256;
    private static final int HEIGHT = 240;

    private final Canvas canvas;
    private final PPU ppu;
    private final CPU cpu;
    private final Controller controller;

    private boolean isRunning = false;

    public EmulatorUI(CPU cpu, PPU ppu) {
        this.cpu = cpu;
        this.ppu = ppu;
        this.controller = new Controller();

        setTitle("NES Emulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
        canvas.setFocusable(true);

        add(canvas);
        pack();
        setLocationRelativeTo(null);

        setupMenuBar();
        setupKeyboardInput();
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem openRomItem = new JMenuItem("Open ROM");
        openRomItem.addActionListener(e -> openRomFile());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(openRomItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    private void setupKeyboardInput() {
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyEvent(e, true);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                handleKeyEvent(e, false);
            }
        });
    }

    private void handleKeyEvent(KeyEvent e, boolean isPressed) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_Z:
                controller.setButton(0, isPressed); // A
                break;
            case KeyEvent.VK_X:
                controller.setButton(1, isPressed); // B
                break;
            case KeyEvent.VK_SPACE:
                controller.setButton(2, isPressed); // Select
                break;
            case KeyEvent.VK_ENTER:
                controller.setButton(3, isPressed); // Start
                break;
            case KeyEvent.VK_UP:
                controller.setButton(4, isPressed); // Up
                break;
            case KeyEvent.VK_DOWN:
                controller.setButton(5, isPressed); // Down
                break;
            case KeyEvent.VK_LEFT:
                controller.setButton(6, isPressed); // Left
                break;
            case KeyEvent.VK_RIGHT:
                controller.setButton(7, isPressed); // Right
                break;
        }
    }

    private void openRomFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open ROM File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".nes");
            }

            @Override
            public String getDescription() {
                return "NES ROM Files (*.nes)";
            }
        });

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            loadRom(selectedFile.getAbsolutePath());
        }
    }

    private void loadRom(String path) {
        stop();

        try {
            start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error loading ROM: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void start() {
        if (isRunning) return;

        isRunning = true;
        setVisible(true);

        Thread renderThread = new Thread(this::renderLoop);
        renderThread.start();
    }

    public void stop() {
        isRunning = false;
    }

    private void renderLoop() {
        canvas.createBufferStrategy(2);
        BufferStrategy bufferStrategy = canvas.getBufferStrategy();

        long lastTime = System.nanoTime();
        double nsPerFrame = 1_000_000_000.0 / 60.0; // 60 FPS

        final int cpuCyclesPerFrame = 29833;
        int cyclesThisFrame = 0;

        while (isRunning) {
            long now = System.nanoTime();
            double delta = (now - lastTime) / nsPerFrame;

            if (delta >= 1.0) {
                cyclesThisFrame = 0;
                while (cyclesThisFrame < cpuCyclesPerFrame) {
                    cpu.runCycle();
                    ppu.runCycle();
                    ppu.runCycle();
                    ppu.runCycle();
                    cyclesThisFrame++;
                }

                render(bufferStrategy);
                lastTime = now;
            } else {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        bufferStrategy.dispose();
    }

    private void render(BufferStrategy bufferStrategy) {
        do {
            do {
                Graphics g = bufferStrategy.getDrawGraphics();

                g.setColor(Color.BLACK);
                g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                g.drawImage(ppu.getFrameBuffer(), 0, 0, WIDTH * SCALE, HEIGHT * SCALE, null);

                g.dispose();
            } while (bufferStrategy.contentsRestored());

            bufferStrategy.show();
        } while (bufferStrategy.contentsLost());
    }

    public Controller getController() {
        return controller;
    }
}
