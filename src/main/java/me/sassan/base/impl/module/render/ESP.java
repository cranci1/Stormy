package me.sassan.base.impl.module.render;

import me.sassan.base.api.module.Module;
import me.sassan.base.api.setting.impl.BooleanSetting;
import me.sassan.base.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import net.weavemc.loader.api.event.RenderWorldEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.BufferUtils;

import java.awt.*;

public class ESP extends Module {
    private final BooleanSetting boxFill = new BooleanSetting("Box Fill", false);

    public ESP() {
        super("ESP", "2D player ESP", Keyboard.KEY_O, Category.VISUAL);
        this.addSetting(boxFill);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null)
            return;
        EntityPlayerSP localPlayer = mc.thePlayer;
        ScaledResolution sr = new ScaledResolution(mc);

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer))
                continue;
            EntityPlayer player = (EntityPlayer) obj;
            if (player == localPlayer || player.isInvisible())
                continue;

            // Interpolate position
            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks()
                    - mc.getRenderManager().viewerPosX;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks()
                    - mc.getRenderManager().viewerPosY;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks()
                    - mc.getRenderManager().viewerPosZ;

            AxisAlignedBB bb = player.getEntityBoundingBox().offset(-player.posX, -player.posY, -player.posZ).offset(x,
                    y, z);

            // Project 8 corners to screen
            double[][] corners = {
                    { bb.minX, bb.minY, bb.minZ },
                    { bb.minX, bb.minY, bb.maxZ },
                    { bb.minX, bb.maxY, bb.minZ },
                    { bb.minX, bb.maxY, bb.maxZ },
                    { bb.maxX, bb.minY, bb.minZ },
                    { bb.maxX, bb.minY, bb.maxZ },
                    { bb.maxX, bb.maxY, bb.minZ },
                    { bb.maxX, bb.maxY, bb.maxZ }
            };

            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            boolean anyInFrustum = false;

            for (double[] c : corners) {
                float[] screen = projectTo2D((float) c[0], (float) c[1], (float) c[2], sr);
                if (screen != null) {
                    anyInFrustum = true;
                    minX = Math.min(minX, screen[0]);
                    minY = Math.min(minY, screen[1]);
                    maxX = Math.max(maxX, screen[0]);
                    maxY = Math.max(maxY, screen[1]);
                }
            }

            if (!anyInFrustum)
                continue;

            int fillColor = new Color(255, 179, 0, 80).getRGB(); // orange/yellow, alpha 80
            int borderColor = new Color(0, 255, 0).getRGB(); // green

            int boxX = (int) minX;
            int boxY = (int) minY;
            int boxW = Math.max(2, (int) (maxX - minX));
            int boxH = Math.max(2, (int) (maxY - minY));

            // Draw fill if enabled
            if (boxFill.getValue()) {
                RenderUtils.drawRect(boxX, boxY, boxW, boxH, fillColor);
            }

            // Draw border (2px)
            RenderUtils.drawRect(boxX, boxY, boxW, 2, borderColor); // top
            RenderUtils.drawRect(boxX, boxY + boxH - 2, boxW, 2, borderColor); // bottom
            RenderUtils.drawRect(boxX, boxY, 2, boxH, borderColor); // left
            RenderUtils.drawRect(boxX + boxW - 2, boxY, 2, boxH, borderColor); // right

            // Draw thin heart bar at left side
            drawHeartBar(boxX - 6, boxY, 4, boxH, player);
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    // Projects world coordinates to 2D screen space using OpenGL matrices
    private float[] projectTo2D(float x, float y, float z, ScaledResolution sr) {
        java.nio.FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
        java.nio.FloatBuffer projection = BufferUtils.createFloatBuffer(16);
        java.nio.IntBuffer viewport = BufferUtils.createIntBuffer(4); // Correct size for viewport
        java.nio.FloatBuffer screenCoords = BufferUtils.createFloatBuffer(3);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        // Rewind all buffers before use
        modelView.rewind();
        projection.rewind();
        viewport.rewind();
        screenCoords.rewind();

        boolean result = GLU.gluProject(x, y, z, modelView, projection, viewport, screenCoords);
        if (!result)
            return null;

        int scale = sr.getScaleFactor();
        screenCoords.rewind();
        float screenX = screenCoords.get(0) / scale;
        float screenY = (sr.getScaledHeight() - screenCoords.get(1) / scale);

        if (screenX < 0 || screenY < 0 || screenX > sr.getScaledWidth() || screenY > sr.getScaledHeight())
            return null;

        return new float[] { screenX, screenY };
    }

    // Draws a thin vertical heart bar at the left side of the box
    private void drawHeartBar(int x, int y, int w, int h, EntityLivingBase entity) {
        int maxHealth = (int) entity.getMaxHealth();
        int health = Math.max(0, Math.min(maxHealth, (int) Math.ceil(entity.getHealth())));
        int barHeight = h - 4;
        int filled = (int) ((health / (float) maxHealth) * barHeight);

        int bgColor = new Color(60, 60, 60, 180).getRGB();
        int fgColor = new Color(220, 20, 60, 220).getRGB(); // red

        // Background
        RenderUtils.drawRect(x, y + 2, w, barHeight, bgColor);
        // Filled
        if (filled > 0) {
            RenderUtils.drawRect(x, y + 2 + (barHeight - filled), w, filled, fgColor);
        }
    }
}
