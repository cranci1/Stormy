package dev.stormy.client.module.modules.render;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.modules.client.AntiBot;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.module.setting.impl.ComboSetting;
import dev.stormy.client.utils.math.MathUtils;
import me.tryfle.stormy.events.RenderLabelEvent;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.nbt.NBTTagList;
import net.weavemc.loader.api.event.RenderLivingEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public class Nametags extends Module {
   public static SliderSetting opacity, scale;
   public static TickSetting shadow, showHealth, showOwnTag, staticSize, background;
   public static TickSetting showDistance, showEquipment, showEnchants;
   public static ComboSetting<HealthMode> healthFormat;

   public enum HealthMode {
      HP, Hearts
   }

   public Nametags() {
      super("Nametags", ModuleCategory.Render, 0);
      this.registerSetting(opacity = new SliderSetting("Opacity", 0.25D, 0.00D, 1.00D, 0.05D));
      this.registerSetting(scale = new SliderSetting("Scale", 1.0D, 0.5D, 2.0D, 0.1D));
      this.registerSetting(shadow = new TickSetting("Text Shadow", false));
      this.registerSetting(showHealth = new TickSetting("Show Health", true));
      this.registerSetting(healthFormat = new ComboSetting<>("Health Format", HealthMode.HP));
      this.registerSetting(showOwnTag = new TickSetting("Show Own Tag", false));
      this.registerSetting(staticSize = new TickSetting("Static Size", false));
      this.registerSetting(background = new TickSetting("Background", true));
      this.registerSetting(showDistance = new TickSetting("Show Distance", true));
      this.registerSetting(showEquipment = new TickSetting("Show Equipment", true));
      this.registerSetting(showEnchants = new TickSetting("Show Enchants", true));
   }

   @SubscribeEvent
   public void onRenderLivingEvent(RenderLivingEvent.Pre event) {
      if (!(event.getEntity() instanceof EntityPlayer))
         return;
      EntityPlayer en = (EntityPlayer) event.getEntity();
      if (!showOwnTag.isToggled() && en == mc.thePlayer)
         return;
      if (en.deathTime != 0)
         return;
      if (AntiBot.bot(en) || en.getDisplayName().getUnformattedText().isEmpty())
         return;

      // Build name tag text
      StringBuilder str = new StringBuilder(en.getDisplayName().getFormattedText());

      // Add health
      if (showHealth.isToggled()) {
         double health = en.getHealth();
         if (healthFormat.getMode() == HealthMode.Hearts)
            health /= 2;
         double r = en.getHealth() / en.getMaxHealth();
         String h = (r < 0.3D ? "§c" : (r < 0.5D ? "§6" : (r < 0.7D ? "§e" : "§a")))
               + MathUtils.round(health, 1)
               + (healthFormat.getMode() == HealthMode.Hearts ? "❤" : "HP");
         str.append(" ").append(h);
      }

      // Add distance
      if (showDistance.isToggled()) {
         double distance = mc.thePlayer.getDistanceToEntity(en);
         str.append(" §7[").append(MathUtils.round(distance, 1)).append("m]");
      }

      String nameTag = str.toString();

      // Render name tag
      GlStateManager.pushMatrix();

      // Position and rotation
      GlStateManager.translate((float) event.getX() + 0.0F, (float) event.getY() + en.height + 0.5F,
            (float) event.getZ());
      GL11.glNormal3f(0.0F, 1.0F, 0.0F);
      GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
      GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);

      // Scale
      float distance = mc.thePlayer.getDistanceToEntity(en);
      float f1 = 0.02666667F * (staticSize.isToggled() ? 1.0F : (distance / 20.0F)) * (float) scale.getInput();
      GlStateManager.scale(-f1, -f1, f1);

      if (en.isSneaking()) {
         GlStateManager.translate(0.0F, 9.374999F, 0.0F);
      }

      // Render setup
      GlStateManager.disableLighting();
      GlStateManager.depthMask(false);
      GlStateManager.disableDepth();
      GlStateManager.enableBlend();
      GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

      // Render background
      if (background.isToggled()) {
         Tessellator tessellator = Tessellator.getInstance();
         WorldRenderer worldrenderer = tessellator.getWorldRenderer();
         int width = mc.fontRendererObj.getStringWidth(nameTag) / 2;

         GlStateManager.disableTexture2D();
         worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
         worldrenderer.pos(-width - 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, (float) opacity.getInput()).endVertex();
         worldrenderer.pos(-width - 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, (float) opacity.getInput()).endVertex();
         worldrenderer.pos(width + 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, (float) opacity.getInput()).endVertex();
         worldrenderer.pos(width + 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, (float) opacity.getInput()).endVertex();
         tessellator.draw();
         GlStateManager.enableTexture2D();
      }

      // Render text
      if (shadow.isToggled()) {
         mc.fontRendererObj.drawStringWithShadow(nameTag, -mc.fontRendererObj.getStringWidth(nameTag) / 2.0F, 0, -1);
      } else {
         mc.fontRendererObj.drawString(nameTag, -mc.fontRendererObj.getStringWidth(nameTag) / 2, 0, -1);
      }

      // Render equipment
      if (showEquipment.isToggled()) {
         int xOffset = -8;
         for (int i = 3; i >= 0; i--) {
            ItemStack stack = en.inventory.armorInventory[i];
            if (stack != null) {
               renderItem(stack, xOffset, -20);
               if (showEnchants.isToggled()) {
                  renderEnchantments(stack, xOffset, -30);
               }
            }
            xOffset += 16;
         }
         // Render held item
         ItemStack heldItem = en.getHeldItem();
         if (heldItem != null) {
            renderItem(heldItem, xOffset, -20);
            if (showEnchants.isToggled()) {
               renderEnchantments(heldItem, xOffset, -30);
            }
         }
      }

      // Cleanup
      GlStateManager.enableDepth();
      GlStateManager.depthMask(true);
      GlStateManager.enableLighting();
      GlStateManager.disableBlend();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      GlStateManager.popMatrix();
   }

   private void renderItem(ItemStack stack, int x, int y) {
      GlStateManager.pushMatrix();
      GlStateManager.translate(x, y, 0);
      GlStateManager.scale(0.5F, 0.5F, 0.5F);
      mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
      GlStateManager.popMatrix();
   }

   private void renderEnchantments(ItemStack stack, int x, int y) {
      NBTTagList enchants = stack.getEnchantmentTagList();
      if (enchants != null) {
         int enchantY = y;
         for (int i = 0; i < enchants.tagCount(); i++) {
            short id = enchants.getCompoundTagAt(i).getShort("id");
            short level = enchants.getCompoundTagAt(i).getShort("lvl");
            Enchantment enchant = Enchantment.getEnchantmentById(id);
            if (enchant != null) {
               String shortName = enchant.getName().substring(0, 2) + level;
               mc.fontRendererObj.drawStringWithShadow(shortName, x, enchantY, 0xFFAAAAAA);
               enchantY -= 8;
            }
         }
      }
   }

   @SubscribeEvent
   public void onRenderLabel(RenderLabelEvent e) {
      if (e.getTarget() instanceof EntityPlayer) {
         e.setCancelled(true);
      }
   }
}