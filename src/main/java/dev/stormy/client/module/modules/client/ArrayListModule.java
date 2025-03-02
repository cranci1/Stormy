package dev.stormy.client.module.modules.client;

import dev.stormy.client.clickgui.ArrayListPosition;
import dev.stormy.client.clickgui.Theme;
import dev.stormy.client.main.Stormy;
import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.ComboSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import dev.stormy.client.utils.render.ColorUtils;
import dev.stormy.client.utils.Utils;
import lombok.Getter;
import net.weavemc.loader.api.event.RenderGameOverlayEvent;
import net.weavemc.loader.api.event.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class ArrayListModule extends Module {
   public static TickSetting editPosition, alphabeticalSort, renderLowerCase, drawBackground, sideLine;
   public static ComboSetting<ColorModes> colorMode;

   @Getter
   public static int hudX = 5;

   @Getter
   public static int hudY = 5;
   public static Utils.HUD.PositionMode positionMode;
   public static final String HUDX_prefix = "HUDX~ ";
   public static final String HUDY_prefix = "HUDY~ ";

   public ArrayListModule() {
      super("ArrayList", Module.ModuleCategory.Client, 0);
      this.registerSetting(colorMode = new ComboSetting<>("Mode", ColorModes.Fade));
      this.registerSetting(editPosition = new TickSetting("Edit position", false));
      this.registerSetting(alphabeticalSort = new TickSetting("Alphabetical sort", false));
      this.registerSetting(renderLowerCase = new TickSetting("Lowercase", true));
      this.registerSetting(drawBackground = new TickSetting("Draw Background", false));
      this.registerSetting(sideLine = new TickSetting("Side Line", false));
   }

   @Override
   public void onEnable() {
      Stormy.moduleManager.sort();
   }

   @Override
   public void guiButtonToggled(TickSetting tick) {
      if (tick == editPosition) {
         editPosition.disable();
         mc.displayGuiScreen(new ArrayListPosition());
      } else if (tick == alphabeticalSort) {
         Stormy.moduleManager.sort();
      }
   }

   @SubscribeEvent
   public void onRender(RenderGameOverlayEvent.Post ev) {
      if (PlayerUtils.isPlayerInGame()) {
         if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
         }

         int margin = 2;
         int y = hudY;

         if (!alphabeticalSort.isToggled()){
            if (positionMode == Utils.HUD.PositionMode.UPLEFT || positionMode == Utils.HUD.PositionMode.UPRIGHT) {
               Stormy.moduleManager.sortShortLong();
            }
            else if(positionMode == Utils.HUD.PositionMode.DOWNLEFT || positionMode == Utils.HUD.PositionMode.DOWNRIGHT) {
               Stormy.moduleManager.sortLongShort();
            }
         }

         List<Module> en = new ArrayList<>(Stormy.moduleManager.getModules());

         if (en.isEmpty()) return;

         int textBoxWidth = Stormy.moduleManager.getLongestActiveModule(mc.fontRendererObj);
         int textBoxHeight = Stormy.moduleManager.getBoxHeight(mc.fontRendererObj, margin);

         if(hudX < 0) {
            hudX = margin;
         }
         if(hudY < 0) {
            {
               hudY = margin;
            }
         }

         if(hudX + textBoxWidth > mc.displayWidth/2){
            hudX = mc.displayWidth/2 - textBoxWidth - margin;
         }

         if(hudY + textBoxHeight > mc.displayHeight/2){
            hudY = mc.displayHeight/2 - textBoxHeight;
         }

         int continuousLineXMin = Integer.MAX_VALUE;
         for (Module m : en) {
            if (m.isEnabled() && m != this) {
               String displayText = (m.getSuffix() != null) ? m.getName() + " - " + m.getSuffix() : m.getName();
               if (renderLowerCase.isToggled()) {
                  displayText = displayText.toLowerCase();
               }
         
               int textWidth = mc.fontRendererObj.getStringWidth(displayText);
               int drawX = hudX;
               if (positionMode == Utils.HUD.PositionMode.DOWNRIGHT || positionMode == Utils.HUD.PositionMode.UPRIGHT) {
                  drawX = hudX + (textBoxWidth - textWidth);
               }
               
               continuousLineXMin = Math.min(continuousLineXMin, drawX);
         
               if (drawBackground.isToggled()) {
                  net.minecraft.client.gui.Gui.drawRect(
                     drawX - 1, 
                     y - 1, 
                     drawX + textWidth + 1, 
                     y + mc.fontRendererObj.FONT_HEIGHT + 1, 
                     Theme.getBackColor().getRGB()
                  );
               }
         
               int color;
               switch (colorMode.getMode()) {
                  case Static:
                     color = Theme.getMainColor().getRGB();
                     break;
                  case Fade:
                     color = ColorUtils.reverseGradientDraw(Theme.getMainColor(), y).getRGB();
                     break;
                  case Breathe:
                     color = ColorUtils.gradientDraw(Theme.getMainColor(), 0).getRGB();
                     break;
                  default:
                     color = Theme.getMainColor().getRGB();
               }
         
               mc.fontRendererObj.drawString(displayText, (float) drawX, (float) y, color, true);
               y += mc.fontRendererObj.FONT_HEIGHT + margin;
            }
         }
         
         if (sideLine.isToggled() && continuousLineXMin != Integer.MAX_VALUE) {
            int linePadding = 2;
            int lineThickness = 2;
            net.minecraft.client.gui.Gui.drawRect(
               continuousLineXMin - lineThickness - linePadding,
               hudY,
               continuousLineXMin - linePadding,
               y,
               Theme.getMainColor().getRGB()
            );
         }
      }

   }

   public enum ColorModes {
      Static, Fade, Breathe
   }

   public static void setHudX(int hudX) {
      ArrayListModule.hudX = hudX;
   }

   public static void setHudY(int hudY) {
      ArrayListModule.hudY = hudY;
   }
}
