package dev.stormy.client.module.modules.render;

import dev.stormy.client.clickgui.Theme;
import dev.stormy.client.module.Module;
import dev.stormy.client.module.modules.client.AntiBot;
import dev.stormy.client.module.setting.impl.ComboSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.utils.Utils;
import dev.stormy.client.utils.player.PlayerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.weavemc.loader.api.event.RenderWorldEvent;
import net.weavemc.loader.api.event.SubscribeEvent;

public class PlayerESP extends Module {
   public static TickSetting redDmg;
   public static ComboSetting<modes> mode;

   public PlayerESP() {
      super("PlayerESP", ModuleCategory.Render, 0);
      this.registerSetting(mode = new ComboSetting<>("Mode", modes.Shaded));
      this.registerSetting(redDmg = new TickSetting("Red on damage", true));
   }

   @Override
   public void onDisable() {
      Utils.HUD.ring_c = false;
   }

   @SubscribeEvent
   public void onRender(RenderWorldEvent e) {
      if (!PlayerUtils.isPlayerInGame() || mc.theWorld == null) return;
      
      final int rgb = Theme.getMainColor().getRGB();
      final boolean redOnDamage = redDmg.isToggled();
      
      for (EntityPlayer en : mc.theWorld.playerEntities) {
         if (en == mc.thePlayer || en.deathTime != 0 || AntiBot.bot(en)) continue;
         callRender(en, rgb, redOnDamage);
      }
   }

   private void callRender(EntityPlayer en, int rgb, boolean redOnDamage) {
      switch (mode.getMode()) {
         case Box:
            Utils.HUD.drawBoxAroundEntity(en, 1, 0.0D, 0.0D, rgb, redOnDamage);
            break;
         case Shaded:
            Utils.HUD.drawBoxAroundEntity(en, 2, 0.0D, 0.0D, rgb, redOnDamage);
            break;
         case Both:
            Utils.HUD.drawBoxAroundEntity(en, 1, 0.0D, 0.0D, rgb, redOnDamage);
            Utils.HUD.drawBoxAroundEntity(en, 2, 0.0D, 0.0D, rgb, redOnDamage);
            break;
      }
   }

   public enum modes {
      Box, Shaded, Both
   }
}