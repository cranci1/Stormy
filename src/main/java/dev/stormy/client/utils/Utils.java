package dev.stormy.client.utils;

import dev.stormy.client.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.List;
import java.util.*;

@SuppressWarnings("unused")
public class Utils {
   private static final Random rand = new Random();
   public static final Minecraft mc = Minecraft.getMinecraft();

   /**
    * Checks if a position or entity is within the player's FOV.
    */
   public static boolean inFov(float fov, Entity entity) {
      return inFov(mc.thePlayer, fov, entity.posX, entity.posZ);
   }

   public static boolean inFov(float fov, double posX, double posZ) {
      return inFov(mc.thePlayer, fov, posX, posZ);
   }

   public static boolean inFov(Entity viewPoint, float fov, double posX, double posZ) {
      // Calculate angle between player and target
      double dx = posX - viewPoint.posX;
      double dz = posZ - viewPoint.posZ;
      double angleToTarget = Math.toDegrees(Math.atan2(dz, dx));
      double playerYaw = MathHelper.wrapAngleTo180_double(viewPoint.rotationYaw);

      // Calculate difference and normalize
      double angleDiff = MathHelper.wrapAngleTo180_double(angleToTarget - playerYaw);
      return Math.abs(angleDiff) <= fov / 2.0;
   }

   /**
    * Returns the yaw angle from the player to the given position.
    */
   public static float angle(double posX, double posZ) {
      double dx = posX - mc.thePlayer.posX;
      double dz = posZ - mc.thePlayer.posZ;
      return (float) Math.toDegrees(Math.atan2(dz, dx));
   }

   public static class Java {

      public static ArrayList<String> toArrayList(String[] fakeList) {
         return new ArrayList<>(Arrays.asList(fakeList));
      }

      public static List<String> StringListToList(String[] whytho) {
         List<String> howTohackNasaWorking2021NoScamDotCom = new ArrayList<>();
         Collections.addAll(howTohackNasaWorking2021NoScamDotCom, whytho);
         return howTohackNasaWorking2021NoScamDotCom;
      }

      public static int randomInt(double inputMin, double v) {
         return (int) (Math.random() * (v - inputMin) + inputMin);
      }
   }

   public static class Distance {
      /**
       * Credit: @AriaJackie/Fractal
       * Calculates the distance to the entity.
       * 
       * @param entity the target entity.
       * @return the distance to the entity.
       */
      public static double distanceToEntity(final EntityPlayer entity) {
         Minecraft mcInstance = Minecraft.getMinecraft();

         float offsetX = (float) (entity.posX - mcInstance.thePlayer.posX);
         float offsetZ = (float) (entity.posZ - mcInstance.thePlayer.posZ);

         return MathHelper.sqrt_double(offsetX * offsetX + offsetZ * offsetZ);
      }

      /**
       * Credit: @AriaJackie/Fractal
       * Calculates the distance to the specified positions.
       * 
       * @param posX the target posX.
       * @param posZ the target posZ.
       * @return the distance to the positions.
       */
      public static double distanceToPoses(final double posX, final double posZ) {
         Minecraft mcInstance = Minecraft.getMinecraft();

         float offsetX = (float) (posX - mcInstance.thePlayer.posX);
         float offsetZ = (float) (posZ - mcInstance.thePlayer.posZ);

         return MathHelper.sqrt_double(offsetX * offsetX + offsetZ * offsetZ);
      }
   }

   public static class HUD {
      private static final Minecraft mc = Minecraft.getMinecraft();
      public static boolean ring_c = false;

      public static void re(BlockPos bp, int color, boolean shade) {
         if (bp != null) {
            double x = (double) bp.getX() - mc.getRenderManager().viewerPosX;
            double y = (double) bp.getY() - mc.getRenderManager().viewerPosY;
            double z = (double) bp.getZ() - mc.getRenderManager().viewerPosZ;

            // Save GL states
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();

            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glLineWidth(2.0F);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            float a = (float) (color >> 24 & 255) / 255.0F;
            float r = (float) (color >> 16 & 255) / 255.0F;
            float g = (float) (color >> 8 & 255) / 255.0F;
            float b = (float) (color & 255) / 255.0F;
            GL11.glColor4d(r, g, b, a);
            RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
            if (shade) {
               dbb(new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), r, g, b);
            }

            // Restore GL states
            GL11.glPopMatrix();
            GL11.glPopAttrib();

            // Additional restore just to be safe
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
            GlStateManager.resetColor();
         }
      }

      /**
       * Renders an ESP box around a block that is visible through walls
       * 
       * @param bp        The BlockPos to render around
       * @param color     The color to use for the box
       * @param filled    Whether to fill the box or just draw lines
       * @param lineWidth The width of the lines for the box
       */
      public static void renderBlockESP(BlockPos bp, int color, boolean filled, float lineWidth) {
         if (bp == null)
            return;

         double x = bp.getX() - mc.getRenderManager().viewerPosX;
         double y = bp.getY() - mc.getRenderManager().viewerPosY;
         double z = bp.getZ() - mc.getRenderManager().viewerPosZ;
         AxisAlignedBB box = new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);

         GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
         GL11.glPushMatrix();

         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GL11.glDisable(GL11.GL_TEXTURE_2D);
         GL11.glDisable(GL11.GL_LIGHTING);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL11.glDepthMask(false);
         GL11.glLineWidth(lineWidth);

         float a = (float) (color >> 24 & 255) / 255.0F;
         float r = (float) (color >> 16 & 255) / 255.0F;
         float g = (float) (color >> 8 & 255) / 255.0F;
         float b = (float) (color & 255) / 255.0F;

         // Draw outline
         GL11.glColor4f(r, g, b, a >= 0.8f ? a : a + 0.2f);
         RenderGlobal.drawSelectionBoundingBox(box);

         // Fill the box if requested
         if (filled) {
            GL11.glColor4f(r, g, b, a * 0.3f);
            drawFilledBox(box);
         }

         // Restore GL states
         GL11.glEnable(GL11.GL_TEXTURE_2D);
         GL11.glEnable(GL11.GL_DEPTH_TEST);
         GL11.glDepthMask(true);
         GL11.glDisable(GL11.GL_BLEND);
         GL11.glPopMatrix();
         GL11.glPopAttrib();
      }

      /**
       * Draw a filled box without normal texture
       * 
       * @param box The bounding box to draw
       */
      public static void drawFilledBox(AxisAlignedBB box) {
         // Top face
         GL11.glBegin(GL11.GL_QUADS);
         GL11.glVertex3d(box.minX, box.maxY, box.minZ);
         GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
         GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
         GL11.glVertex3d(box.minX, box.maxY, box.maxZ);

         // Bottom face
         GL11.glVertex3d(box.minX, box.minY, box.minZ);
         GL11.glVertex3d(box.maxX, box.minY, box.minZ);
         GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
         GL11.glVertex3d(box.minX, box.minY, box.maxZ);

         // North face
         GL11.glVertex3d(box.minX, box.minY, box.minZ);
         GL11.glVertex3d(box.minX, box.maxY, box.minZ);
         GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
         GL11.glVertex3d(box.maxX, box.minY, box.minZ);

         // South face
         GL11.glVertex3d(box.minX, box.minY, box.maxZ);
         GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
         GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
         GL11.glVertex3d(box.maxX, box.minY, box.maxZ);

         // West face
         GL11.glVertex3d(box.minX, box.minY, box.minZ);
         GL11.glVertex3d(box.minX, box.minY, box.maxZ);
         GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
         GL11.glVertex3d(box.minX, box.maxY, box.minZ);

         // East face
         GL11.glVertex3d(box.maxX, box.minY, box.minZ);
         GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
         GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
         GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
         GL11.glEnd();
      }

      /**
       * Draw a traceline from player to a position
       */
      public static void drawTraceToBlock(BlockPos pos, int color, float lineWidth) {
         double x = pos.getX() + 0.5 - mc.getRenderManager().viewerPosX;
         double y = pos.getY() + 0.5 - mc.getRenderManager().viewerPosY;
         double z = pos.getZ() + 0.5 - mc.getRenderManager().viewerPosZ;

         // Eyes position
         double eyeX = 0;
         double eyeY = 0;
         double eyeZ = 0;

         GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
         GL11.glPushMatrix();

         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GL11.glDisable(GL11.GL_TEXTURE_2D);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL11.glDepthMask(false);
         GL11.glLineWidth(lineWidth);

         // Extract color components
         float a = (float) (color >> 24 & 255) / 255.0F;
         float r = (float) (color >> 16 & 255) / 255.0F;
         float g = (float) (color >> 8 & 255) / 255.0F;
         float b = (float) (color & 255) / 255.0F;
         GL11.glColor4f(r, g, b, a);

         // Draw line
         GL11.glBegin(GL11.GL_LINES);
         GL11.glVertex3d(eyeX, eyeY, eyeZ);
         GL11.glVertex3d(x, y, z);
         GL11.glEnd();

         // Restore GL states
         GL11.glEnable(GL11.GL_TEXTURE_2D);
         GL11.glEnable(GL11.GL_DEPTH_TEST);
         GL11.glDepthMask(true);
         GL11.glDisable(GL11.GL_BLEND);
         GL11.glPopMatrix();
         GL11.glPopAttrib();
      }

      public static void drawBoxAroundEntity(Entity e, int type, double expand, double shift, int color,
            boolean damage) {
         if (e instanceof EntityLivingBase) {
            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * (double) mc.timer.renderPartialTicks
                  - mc.getRenderManager().viewerPosX;
            double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * (double) mc.timer.renderPartialTicks
                  - mc.getRenderManager().viewerPosY;
            double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * (double) mc.timer.renderPartialTicks
                  - mc.getRenderManager().viewerPosZ;
            float d = (float) expand / 40.0F;
            if (e instanceof EntityPlayer && damage && ((EntityPlayer) e).hurtTime != 0) {
               color = Color.RED.getRGB();
            }

            // Save GL state
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GlStateManager.pushMatrix();

            if (type == 3) {
               GL11.glTranslated(x, y - 0.2D, z);
               GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
               GlStateManager.disableDepth();
               GL11.glScalef(0.03F + d, 0.03F + d, 0.03F + d);
               int outline = Color.black.getRGB();
               net.minecraft.client.gui.Gui.drawRect(-20, -1, -26, 75, outline);
               net.minecraft.client.gui.Gui.drawRect(20, -1, 26, 75, outline);
               net.minecraft.client.gui.Gui.drawRect(-20, -1, 21, 5, outline);
               net.minecraft.client.gui.Gui.drawRect(-20, 70, 21, 75, outline);
               if (color != 0) {
                  net.minecraft.client.gui.Gui.drawRect(-21, 0, -25, 74, color);
                  net.minecraft.client.gui.Gui.drawRect(21, 0, 25, 74, color);
                  net.minecraft.client.gui.Gui.drawRect(-21, 0, 24, 4, color);
                  net.minecraft.client.gui.Gui.drawRect(-21, 71, 25, 74, color);
               }

               GlStateManager.enableDepth();
            } else {
               int i;
               if (type == 4) {
                  EntityLivingBase en = (EntityLivingBase) e;
                  double r = en.getHealth() / en.getMaxHealth();
                  int b = (int) (74.0D * r);
                  int hc = r < 0.3D ? Color.red.getRGB()
                        : (r < 0.5D ? Color.orange.getRGB()
                              : (r < 0.7D ? Color.yellow.getRGB() : Color.green.getRGB()));
                  GL11.glTranslated(x, y - 0.2D, z);
                  GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
                  GlStateManager.disableDepth();
                  GL11.glScalef(0.03F + d, 0.03F + d, 0.03F + d);
                  i = (int) (21.0D + shift * 2.0D);
                  net.minecraft.client.gui.Gui.drawRect(i, -1, i + 5, 75, Color.black.getRGB());
                  net.minecraft.client.gui.Gui.drawRect(i + 1, b, i + 4, 74, Color.darkGray.getRGB());
                  net.minecraft.client.gui.Gui.drawRect(i + 1, 0, i + 4, b, hc);
                  GlStateManager.enableDepth();
               } else if (type == 6) {
                  d3p(x, y, z, 0.699999988079071D, 45, 1.5F, color, color == 0);
               } else {
                  float a = (float) (color >> 24 & 255) / 255.0F;
                  float r = (float) (color >> 16 & 255) / 255.0F;
                  float g = (float) (color >> 8 & 255) / 255.0F;
                  float b = (float) (color & 255) / 255.0F;
                  if (type == 5) {
                     GL11.glTranslated(x, y - 0.2D, z);
                     GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
                     GlStateManager.disableDepth();
                     GL11.glScalef(0.03F + d, 0.03F, 0.03F + d);
                     d2p(0.0D, 95.0D, 10, 3, Color.black.getRGB());

                     for (i = 0; i < 6; ++i) {
                        d2p(0.0D, 95 + (10 - i), 3, 4, Color.black.getRGB());
                     }

                     for (i = 0; i < 7; ++i) {
                        d2p(0.0D, 95 + (10 - i), 2, 4, color);
                     }

                     d2p(0.0D, 95.0D, 8, 3, color);
                     GlStateManager.enableDepth();
                  } else {
                     AxisAlignedBB bbox = e.getEntityBoundingBox().expand(0.1D + expand, 0.1D + expand, 0.1D + expand);
                     AxisAlignedBB axis = new AxisAlignedBB(bbox.minX - e.posX + x, bbox.minY - e.posY + y,
                           bbox.minZ - e.posZ + z, bbox.maxX - e.posX + x, bbox.maxY - e.posY + y,
                           bbox.maxZ - e.posZ + z);
                     GL11.glBlendFunc(770, 771);
                     GL11.glEnable(3042);
                     GL11.glDisable(3553);
                     GL11.glDisable(2929);
                     GL11.glDepthMask(false);
                     GL11.glLineWidth(2.0F);
                     GL11.glColor4f(r, g, b, a);
                     if (type == 1) {
                        RenderGlobal.drawSelectionBoundingBox(axis);
                     } else if (type == 2) {
                        dbb(axis, r, g, b);
                     }

                     GL11.glEnable(3553);
                     GL11.glEnable(2929);
                     GL11.glDepthMask(true);
                     GL11.glDisable(3042);
                  }
               }
            }

            // Restore GL state
            GlStateManager.popMatrix();
            GL11.glPopAttrib();

            // Additional reset to ensure proper state
            GlStateManager.resetColor();
            GlStateManager.enableDepth();
         }
      }

      /**
       * Improved method for drawing wall-hack style outlines
       * 
       * @param entity    The entity to draw around
       * @param color     The color to use
       * @param lineWidth Line width for outline
       * @param outline   Whether to draw outline
       * @param fill      Whether to fill the box
       */
      public static void drawEntityESP(Entity entity, int color, float lineWidth, boolean outline, boolean fill) {
         if (entity == null)
            return;

         double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * mc.timer.renderPartialTicks
               - mc.getRenderManager().viewerPosX;
         double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * mc.timer.renderPartialTicks
               - mc.getRenderManager().viewerPosY;
         double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * mc.timer.renderPartialTicks
               - mc.getRenderManager().viewerPosZ;

         AxisAlignedBB box = new AxisAlignedBB(
               x - entity.width / 2, y, z - entity.width / 2,
               x + entity.width / 2, y + entity.height, z + entity.width / 2);

         // Extract color components
         float a = (float) (color >> 24 & 255) / 255.0F;
         float r = (float) (color >> 16 & 255) / 255.0F;
         float g = (float) (color >> 8 & 255) / 255.0F;
         float b = (float) (color & 255) / 255.0F;

         // Save GL state
         GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
         GL11.glPushMatrix();

         // Setup rendering mode
         GL11.glDisable(GL11.GL_TEXTURE_2D);
         GL11.glDisable(GL11.GL_LIGHTING);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GL11.glLineWidth(lineWidth);
         GL11.glEnable(GL11.GL_LINE_SMOOTH);

         // Draw filled box if requested
         if (fill) {
            GL11.glColor4f(r, g, b, a * 0.3f);
            drawFilledBox(box);
         }

         // Draw outline if requested
         if (outline) {
            GL11.glColor4f(r, g, b, a);
            RenderGlobal.drawSelectionBoundingBox(box);
         }

         // Restore GL state
         GL11.glDisable(GL11.GL_BLEND);
         GL11.glEnable(GL11.GL_TEXTURE_2D);
         GL11.glEnable(GL11.GL_DEPTH_TEST);
         GL11.glDisable(GL11.GL_LINE_SMOOTH);
         GL11.glPopMatrix();
         GL11.glPopAttrib();
      }

      public static void dbb(AxisAlignedBB abb, float r, float g, float b) {
         float a = 0.25F;
         Tessellator ts = Tessellator.getInstance();
         WorldRenderer vb = ts.getWorldRenderer();

         // Save GL state
         GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

         vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
         vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         ts.draw();
         vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
         vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         ts.draw();
         vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
         vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         ts.draw();
         vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
         vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         ts.draw();
         vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
         vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         ts.draw();
         vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
         vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
         vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
         ts.draw();

         // Restore GL state
         GL11.glPopAttrib();
      }

      /**
       * Draw a 2D outline around a block with color gradient
       */
      public static void drawOutline2D(BlockPos pos, Color color, float lineWidth) {
         double x = pos.getX() - mc.getRenderManager().viewerPosX;
         double y = pos.getY() - mc.getRenderManager().viewerPosY;
         double z = pos.getZ() - mc.getRenderManager().viewerPosZ;

         GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
         GL11.glPushMatrix();

         GL11.glTranslated(x, y, z);
         GL11.glNormal3f(0.0F, 1.0F, 0.0F);
         GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);

         float scale = 0.016666668F * 1.6F;
         GL11.glScalef(-scale, -scale, scale);

         GL11.glDisable(GL11.GL_TEXTURE_2D);
         GL11.glDisable(GL11.GL_DEPTH_TEST);
         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GL11.glEnable(GL11.GL_LINE_SMOOTH);

         GL11.glLineWidth(lineWidth);
         GL11.glBegin(GL11.GL_LINE_LOOP);
         GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
               color.getAlpha() / 255f);

         // Draw a square around the block
         GL11.glVertex2f(-15, 15);
         GL11.glVertex2f(15, 15);
         GL11.glVertex2f(15, -15);
         GL11.glVertex2f(-15, -15);
         GL11.glEnd();

         GL11.glDisable(GL11.GL_LINE_SMOOTH);
         GL11.glEnable(GL11.GL_TEXTURE_2D);
         GL11.glEnable(GL11.GL_DEPTH_TEST);
         GL11.glDisable(GL11.GL_BLEND);

         GL11.glPopMatrix();
         GL11.glPopAttrib();
      }

      public static PositionMode getPostitionMode(int marginX, int marginY, double height, double width) {
         int halfHeight = (int) (height / 4);
         int halfWidth = (int) width;
         PositionMode positionMode = null;

         if (marginY < halfHeight) {
            if (marginX < halfWidth) {
               positionMode = PositionMode.UPLEFT;
            }
            if (marginX > halfWidth) {
               positionMode = PositionMode.UPRIGHT;
            }
         }

         if (marginY > halfHeight) {
            if (marginX < halfWidth) {
               positionMode = PositionMode.DOWNLEFT;
            }
            if (marginX > halfWidth) {
               positionMode = PositionMode.DOWNRIGHT;
            }
         }

         return positionMode;
      }

      public static void d2p(double x, double y, int radius, int sides, int color) {
         float a = (float) (color >> 24 & 255) / 255.0F;
         float r = (float) (color >> 16 & 255) / 255.0F;
         float g = (float) (color >> 8 & 255) / 255.0F;
         float b = (float) (color & 255) / 255.0F;
         Tessellator tessellator = Tessellator.getInstance();
         WorldRenderer worldrenderer = tessellator.getWorldRenderer();
         GlStateManager.enableBlend();
         GlStateManager.disableTexture2D();
         GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
         GlStateManager.color(r, g, b, a);
         worldrenderer.begin(6, DefaultVertexFormats.POSITION);

         for (int i = 0; i < sides; ++i) {
            double angle = 6.283185307179586D * (double) i / (double) sides + Math.toRadians(180.0D);
            worldrenderer.pos(x + Math.sin(angle) * (double) radius, y + Math.cos(angle) * (double) radius, 0.0D)
                  .endVertex();
         }

         tessellator.draw();
         GlStateManager.enableTexture2D();
         GlStateManager.disableBlend();
      }

      public static void d3p(double x, double y, double z, double radius, int sides, float lineWidth, int color,
            boolean chroma) {
         float a = (float) (color >> 24 & 255) / 255.0F;
         float r = (float) (color >> 16 & 255) / 255.0F;
         float g = (float) (color >> 8 & 255) / 255.0F;
         float b = (float) (color & 255) / 255.0F;
         mc.entityRenderer.disableLightmap();
         GL11.glDisable(3553);
         GL11.glEnable(3042);
         GL11.glBlendFunc(770, 771);
         GL11.glDisable(2929);
         GL11.glEnable(2848);
         GL11.glDepthMask(false);
         GL11.glLineWidth(lineWidth);
         if (!chroma) {
            GL11.glColor4f(r, g, b, a);
         }

         GL11.glBegin(1);
         long d = 0L;
         long ed = 15000L / (long) sides;
         long hed = ed / 2L;

         for (int i = 0; i < sides * 2; ++i) {
            if (chroma) {
               if (i % 2 != 0) {
                  if (i == 47) {
                     d = hed;
                  }

                  d += ed;
               }

               int c = RenderUtils.rainbowDraw(2L, d);
               float r2 = (float) (c >> 16 & 255) / 255.0F;
               float g2 = (float) (c >> 8 & 255) / 255.0F;
               float b2 = (float) (c & 255) / 255.0F;
               GL11.glColor3f(r2, g2, b2);
            }

            double angle = 6.283185307179586D * (double) i / (double) sides + Math.toRadians(180.0D);
            GL11.glVertex3d(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
         }

         GL11.glEnd();
         GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
         GL11.glDepthMask(true);
         GL11.glDisable(2848);
         GL11.glEnable(2929);
         GL11.glDisable(3042);
         GL11.glEnable(3553);
         mc.entityRenderer.enableLightmap();
      }

      public enum PositionMode {
         UPLEFT,
         UPRIGHT,
         DOWNLEFT,
         DOWNRIGHT
      }

      public static void drawRoundedRect(float left, float top, float right, float bottom, float radius, Color color) {
         drawRect(left + radius, top, right - radius, bottom, color);
         drawRect(left, top + radius, left + radius, bottom - radius, color);
         drawRect(right - radius, top + radius, right, bottom - radius, color);
         
         drawFilledCircle(left + radius, top + radius, radius, color);
         drawFilledCircle(right - radius, top + radius, radius, color);
         drawFilledCircle(left + radius, bottom - radius, radius, color);
         drawFilledCircle(right - radius, bottom - radius, radius, color);
     }
     
     public static void drawRect(float left, float top, float right, float bottom, Color color) {
         float f3;
         
         if (left < right) {
             f3 = left;
             left = right;
             right = f3;
         }
         
         if (top < bottom) {
             f3 = top;
             top = bottom;
             bottom = f3;
         }
         
         float alpha = (color.getRGB() >> 24 & 0xFF) / 255.0F;
         float red = (color.getRGB() >> 16 & 0xFF) / 255.0F;
         float green = (color.getRGB() >> 8 & 0xFF) / 255.0F;
         float blue = (color.getRGB() & 0xFF) / 255.0F;
         
         Tessellator tessellator = Tessellator.getInstance();
         WorldRenderer worldrenderer = tessellator.getWorldRenderer();
         
         GlStateManager.enableBlend();
         GlStateManager.disableTexture2D();
         GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
         GlStateManager.color(red, green, blue, alpha);
         
         worldrenderer.begin(7, DefaultVertexFormats.POSITION);
         worldrenderer.pos(left, bottom, 0.0D).endVertex();
         worldrenderer.pos(right, bottom, 0.0D).endVertex();
         worldrenderer.pos(right, top, 0.0D).endVertex();
         worldrenderer.pos(left, top, 0.0D).endVertex();
         tessellator.draw();
         
         GlStateManager.enableTexture2D();
         GlStateManager.disableBlend();
     }
     
     public static void drawFilledCircle(float x, float y, float radius, Color color) {
         GlStateManager.enableBlend();
         GlStateManager.disableTexture2D();
         GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
         
         float alpha = (color.getRGB() >> 24 & 0xFF) / 255.0F;
         float red = (color.getRGB() >> 16 & 0xFF) / 255.0F;
         float green = (color.getRGB() >> 8 & 0xFF) / 255.0F;
         float blue = (color.getRGB() & 0xFF) / 255.0F;
         
         GlStateManager.color(red, green, blue, alpha);
         
         GL11.glBegin(GL11.GL_TRIANGLE_FAN);
         GL11.glVertex2f(x, y);
         
         for (int i = 0; i <= 360; i++) {
             float angle = (float) Math.toRadians(i);
             GL11.glVertex2f((float) (x + Math.sin(angle) * radius), (float) (y + Math.cos(angle) * radius));
         }
         
         GL11.glEnd();
         
         GlStateManager.enableTexture2D();
         GlStateManager.disableBlend();
     }
   }
}