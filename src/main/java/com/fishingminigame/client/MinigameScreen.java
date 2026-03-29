package com.fishingminigame.client;

import com.fishingminigame.network.MinigameResultPacket;
import com.fishingminigame.network.PacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.awt.*;

public class MinigameScreen extends Screen {

    // ── Config ──────────────────────────────────────────────
    private static final int MAX_CATCHES = 4;
    private static final int RING_RADIUS = 80;
    private static final int RING_THICKNESS = 18;

    // Golden zone arc sizes (degrees) per catch level 0-3
    private static final float[] GOLDEN_ARC_DEG = {40f, 28f, 20f, 14f};

    // Base angular speed (radians/tick) and multiplier per catch
    private static final float BASE_SPEED   = 0.045f;
    private static final float SPEED_FACTOR = 1.35f;

    // How many ticks the zone flashes green on a hit before moving on
    private static final int FLASH_TICKS = 12;
    // ──────────────────────────────────────────────────────────

    private final int bobberEntityId;

    private int catches = 0;
    private boolean gameOver = false;

    // Current needle angle in radians (0 = right)
    private float needleAngle = 0f;
    // Golden zone start angle (radians)
    private float goldenStart = 0f;
    // Golden zone arc size (radians)
    private float goldenArc = 0f;

    private float currentSpeed = BASE_SPEED;

    // Flash state
    private boolean flashing = false;
    private boolean flashHit = false; // true = hit (green), false = miss (red)
    private int flashTimer = 0;

    // Finished — waiting for result to send
    private boolean resultSent = false;

    public MinigameScreen(int bobberEntityId) {
        super(Component.literal("Fishing!"));
        this.bobberEntityId = bobberEntityId;
        randomiseZone();
    }

    // ── Lifecycle ────────────────────────────────────────────

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
    }

    // ── Tick ─────────────────────────────────────────────────

    @Override
    public void tick() {
        if (gameOver) {
            if (flashing) {
                flashTimer--;
                if (flashTimer <= 0) {
                    flashing = false;
                    sendResult();
                }
            }
            return;
        }

        if (flashing) {
            flashTimer--;
            if (flashTimer <= 0) {
                flashing = false;
                if (catches >= MAX_CATCHES) {
                    // Max catches reached — auto finish
                    finishGame();
                } else {
                    // Prepare next round
                    randomiseZone();
                    updateSpeed();
                }
            }
            return;
        }

        needleAngle = (needleAngle + currentSpeed) % (float)(Math.PI * 2);
    }

    // ── Input ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !flashing && !gameOver) {
            handleClick();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Space or right-click (key 32 = space) also triggers
        if ((keyCode == 32 || keyCode == 258) && !flashing && !gameOver) {
            handleClick();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void handleClick() {
        if (isNeedleInGolden()) {
            catches++;
            flashHit = true;
            flashing = true;
            flashTimer = FLASH_TICKS;
            if (catches >= MAX_CATCHES) {
                gameOver = true; // will send after flash
            }
        } else {
            // Miss — game over immediately
            flashHit = false;
            flashing = true;
            flashTimer = FLASH_TICKS;
            gameOver = true;
        }
    }

    // ── Render ────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Semi-transparent dark backdrop
        gfx.fill(0, 0, this.width, this.height, 0x88000000);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // --- Draw ring segments ---
        // We draw the ring by rendering thin arc slices as filled quads using GL
        // Use Minecraft's filled-rect approach by drawing lots of thin rotated quads.
        // Simpler approach: draw a thick circle outline using pixel-level checks via
        // multiple drawPixel calls would be too slow; instead we blit arc sectors.

        // Draw full ring (dark gray background ring)
        drawArc(gfx, cx, cy, RING_RADIUS, RING_THICKNESS, 0f, (float)(Math.PI * 2), 0xFF3A3A3A);

        // Draw golden zone
        int goldenColor = 0xFFE6AA2C;
        if (flashing) {
            goldenColor = flashHit ? 0xFF44CC44 : 0xFFCC2222;
        }
        drawArc(gfx, cx, cy, RING_RADIUS, RING_THICKNESS, goldenStart, goldenStart + goldenArc, goldenColor);

        // Draw needle (bright red dot on ring edge)
        float nx = cx + (float)(Math.cos(needleAngle) * RING_RADIUS);
        float ny = cy + (float)(Math.sin(needleAngle) * RING_RADIUS);
        int nr = RING_THICKNESS / 2 + 3;
        gfx.fill((int)nx - nr, (int)ny - nr, (int)nx + nr, (int)ny + nr, 0xFFCC2222);
        // Inner bright
        int nri = RING_THICKNESS / 2 - 1;
        gfx.fill((int)nx - nri, (int)ny - nri, (int)nx + nri, (int)ny + nri, 0xFFFF6666);

        // --- Center HUD ---
        // Catch dots
        int dotSize = 12, dotGap = 6;
        int totalDotW = MAX_CATCHES * dotSize + (MAX_CATCHES - 1) * dotGap;
        int dotStartX = cx - totalDotW / 2;
        int dotY = cy - 16;
        for (int i = 0; i < MAX_CATCHES; i++) {
            int dotX = dotStartX + i * (dotSize + dotGap);
            int color = (i < catches) ? 0xFFE6AA2C : 0xFF555555;
            gfx.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, color);
        }

        // Instruction text
        String msg;
        if (gameOver && flashing) {
            msg = flashHit ? catches + " catches!" : (catches > 0 ? catches + " catch" + (catches > 1 ? "es" : "") + "!" : "Missed!");
        } else {
            msg = "Click to reel!";
        }
        int textW = this.font.width(msg);
        gfx.drawString(this.font, msg, cx - textW / 2, cy + 10, 0xFFFFFFFF);

        // Speed indicator
        String speedStr = "Speed: " + (Math.round((currentSpeed / BASE_SPEED) * 10f) / 10f) + "x";
        int speedW = this.font.width(speedStr);
        gfx.drawString(this.font, speedStr, cx - speedW / 2, cy + 25, 0xFFAAAAAA);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    // ── Helpers ───────────────────────────────────────────────

    private void randomiseZone() {
        int level = Math.min(catches, GOLDEN_ARC_DEG.length - 1);
        goldenArc = (float)(GOLDEN_ARC_DEG[level] * Math.PI / 180.0);
        goldenStart = (float)(Math.random() * Math.PI * 2);
    }

    private void updateSpeed() {
        // Each catch multiplies speed
        currentSpeed = BASE_SPEED * (float)Math.pow(SPEED_FACTOR, catches);
    }

    private boolean isNeedleInGolden() {
        float a = ((needleAngle % (float)(Math.PI * 2)) + (float)(Math.PI * 2)) % (float)(Math.PI * 2);
        float gs = ((goldenStart % (float)(Math.PI * 2)) + (float)(Math.PI * 2)) % (float)(Math.PI * 2);
        float ge = gs + goldenArc;
        if (ge > (float)(Math.PI * 2)) {
            return a >= gs || a <= (ge - (float)(Math.PI * 2));
        }
        return a >= gs && a <= ge;
    }

    private void finishGame() {
        gameOver = true;
        flashing = true;
        flashTimer = FLASH_TICKS;
    }

    private void sendResult() {
        if (resultSent) return;
        resultSent = true;
        PacketHandler.CHANNEL.sendToServer(new MinigameResultPacket(catches, bobberEntityId));
        this.onClose();
    }

    /** Draws a thick arc segment on the ring. */
    private void drawArc(GuiGraphics gfx, int cx, int cy, int radius, int thickness,
                         float startAngle, float endAngle, int color) {
        int steps = (int)(Math.abs(endAngle - startAngle) / (Math.PI * 2) * 360) + 1;
        steps = Math.max(steps, 3);
        float step = (endAngle - startAngle) / steps;
        int innerR = radius - thickness / 2;
        int outerR = radius + thickness / 2;

        for (int i = 0; i < steps; i++) {
            float a1 = startAngle + i * step;
            float a2 = a1 + step;
            float cos1 = (float)Math.cos(a1), sin1 = (float)Math.sin(a1);
            float cos2 = (float)Math.cos(a2), sin2 = (float)Math.sin(a2);

            int x1i = cx + (int)(cos1 * innerR), y1i = cy + (int)(sin1 * innerR);
            int x1o = cx + (int)(cos1 * outerR), y1o = cy + (int)(sin1 * outerR);
            int x2i = cx + (int)(cos2 * innerR), y2i = cy + (int)(sin2 * innerR);
            int x2o = cx + (int)(cos2 * outerR), y2o = cy + (int)(sin2 * outerR);

            // Draw as two triangles using gfx.fill on bounding box — approximate
            int minX = Math.min(Math.min(x1i, x1o), Math.min(x2i, x2o));
            int maxX = Math.max(Math.max(x1i, x1o), Math.max(x2i, x2o));
            int minY = Math.min(Math.min(y1i, y1o), Math.min(y2i, y2o));
            int maxY = Math.max(Math.max(y1i, y1o), Math.max(y2i, y2o));
            gfx.fill(minX, minY, maxX + 1, maxY + 1, color);
        }
    }
}
