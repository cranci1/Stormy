package me.sassan.base.utils.render;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.awt.*;

/**
 * @author sassan
 *         18.11.2023, 2023
 */
public class RenderUtils {
    public static void drawRect(int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + height, color);
    }

    public static void drawString(FontRenderer fontRenderer, String text, int x, int y, int color) {
        fontRenderer.drawString(text, x, y, color);
    }

    /**
     * Draws a rounded rectangle with the specified parameters
     * 
     * @param x      X position
     * @param y      Y position
     * @param width  Width
     * @param height Height
     * @param radius Corner radius
     * @param color  Color (ARGB)
     */
    public static void drawRoundedRect(int x, int y, int width, int height, int radius, int color) {
        // Draw the main rectangle without corners
        drawRect(x + radius, y, width - 2 * radius, height, color);
        drawRect(x, y + radius, width, height - 2 * radius, color);

        // Draw the four corners
        drawFilledCircle(x + radius, y + radius, radius, color);
        drawFilledCircle(x + width - radius, y + radius, radius, color);
        drawFilledCircle(x + radius, y + height - radius, radius, color);
        drawFilledCircle(x + width - radius, y + height - radius, radius, color);
    }

    /**
     * Draws a filled circle at the specified location
     * 
     * @param x      Center X
     * @param y      Center Y
     * @param radius Radius
     * @param color  Color (ARGB)
     */
    public static void drawFilledCircle(int x, int y, int radius, int color) {
        float alpha = (color >> 24 & 0xFF) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(red, green, blue, alpha);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION);

        // Center vertex
        worldrenderer.pos(x, y, 0).endVertex();

        // Draw vertices around the circumference
        for (int i = 0; i <= 360; i++) {
            double radian = Math.toRadians(i);
            double xPos = x + Math.sin(radian) * radius;
            double yPos = y + Math.cos(radian) * radius;
            worldrenderer.pos(xPos, yPos, 0).endVertex();
        }

        tessellator.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
