package dev.stormy.client.module.modules.combat;

import dev.stormy.client.main.Stormy;
import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DoubleSliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.module.setting.impl.DescriptionSetting;
import dev.stormy.client.utils.player.PlayerUtils;
import dev.stormy.client.utils.math.MathUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.weavemc.loader.api.event.MouseEvent;
import net.weavemc.loader.api.event.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.List;
import java.util.Random;

public class Reach extends Module {
   public static DoubleSliderSetting reachDist;
   public static TickSetting weaponOnly, movingOnly, sprintOnly, hitThroughBlocks;

   public Reach() {
      super("Reach", ModuleCategory.Combat, 0);
      this.registerSetting(new DescriptionSetting("Increases your reach."));
      this.registerSetting(reachDist = new DoubleSliderSetting("Reach", 3.07D, 3.10D, 3.0D, 3.5D, 0.01D));
      this.registerSetting(weaponOnly = new TickSetting("Weapon only", false));
      this.registerSetting(movingOnly = new TickSetting("Moving only", false));
      this.registerSetting(sprintOnly = new TickSetting("Sprint only", false));
      this.registerSetting(hitThroughBlocks = new TickSetting("Hit through blocks", false));
   }

   public void guiUpdate() {
      if (reachDist.getInputMin() > reachDist.getInputMax()) {
         double temp = reachDist.getInputMin();
         setInputMin(reachDist, reachDist.getInputMax());
         setInputMax(reachDist, temp);
      }
   }

   private void setInputMin(DoubleSliderSetting setting, double value) {
      try {
         java.lang.reflect.Field f = DoubleSliderSetting.class.getDeclaredField("inputMin");
         f.setAccessible(true);
         f.set(setting, value);
      } catch (Exception ignored) {
      }
   }

   private void setInputMax(DoubleSliderSetting setting, double value) {
      try {
         java.lang.reflect.Field f = DoubleSliderSetting.class.getDeclaredField("inputMax");
         f.setAccessible(true);
         f.set(setting, value);
      } catch (Exception ignored) {
      }
   }

   @SubscribeEvent
   public void onMouse(MouseEvent e) {
      int button = getMouseEventButton(e);
      boolean buttonState = getMouseEventButtonState(e);
      if (button >= 0 && buttonState && PlayerUtils.isPlayerInGame() && Mouse.isButtonDown(0)) {
         callReach();
      }
   }

   private int getMouseEventButton(MouseEvent e) {
      try {
         java.lang.reflect.Field f = e.getClass().getDeclaredField("button");
         f.setAccessible(true);
         return f.getInt(e);
      } catch (Exception ex) {
         return -1;
      }
   }

   private boolean getMouseEventButtonState(MouseEvent e) {
      try {
         java.lang.reflect.Field f = e.getClass().getDeclaredField("buttonstate");
         f.setAccessible(true);
         return f.getBoolean(e);
      } catch (Exception ex) {
         return false;
      }
   }

   public static double getRandomReach(DoubleSliderSetting setting, Random r) {
      double min = setting.getInputMin();
      double max = setting.getInputMax();
      return min == max ? min : min + r.nextDouble() * (max - min);
   }

   private static boolean holdingWeapon() {
      if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null)
         return false;
      return isWeapon(mc.thePlayer.getHeldItem().getItem());
   }

   private static boolean isWeapon(Object item) {
      return item instanceof ItemSword ||
            item instanceof ItemAxe ||
            item instanceof ItemPickaxe ||
            item instanceof ItemSpade;
   }

   public static boolean callReach() {
      if (!PlayerUtils.isPlayerInGame()) {
         return false;
      } else if (weaponOnly.isToggled() && !holdingWeapon()) {
         return false;
      } else if (movingOnly.isToggled() && mc.thePlayer.moveForward == 0.0D && mc.thePlayer.moveStrafing == 0.0D) {
         return false;
      } else if (sprintOnly.isToggled() && !mc.thePlayer.isSprinting()) {
         return false;
      } else {
         if (!hitThroughBlocks.isToggled() && mc.objectMouseOver != null) {
            BlockPos p = mc.objectMouseOver.getBlockPos();
            if (p != null && mc.theWorld.getBlockState(p).getBlock() != Blocks.air) {
               return false;
            }
         }
         double reach = getRandomReach(reachDist, MathUtils.rand());
         Object[] result = findEntity(reach, 0.0D);
         if (result == null) {
            return false;
         } else {
            Entity en = (Entity) result[0];
            mc.objectMouseOver = new MovingObjectPosition(en, (Vec3) result[1]);
            mc.pointedEntity = en;
            return true;
         }
      }
   }

   private static Object[] findEntity(double reach, double expand) {
      Module reachModule = Stormy.moduleManager.getModuleByClazz(Reach.class);
      if (reachModule != null && !reachModule.isEnabled()) {
         reach = mc.playerController.extendedReach() ? 6.0D : 3.0D;
      }
      return findEntity(reach, expand, null);
   }

   public static Object[] findEntity(double reach, double expand, float[] rotations) {
      Entity renderView = mc.getRenderViewEntity();
      Entity target = null;
      if (renderView == null)
         return null;

      mc.mcProfiler.startSection("pick");
      Vec3 eyePos = renderView.getPositionEyes(1.0F);
      Vec3 lookVec = rotations != null
            ? getVectorForRotation(rotations[1], rotations[0])
            : renderView.getLook(1.0F);
      Vec3 reachVec = eyePos.addVector(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach);
      Vec3 hitVec = null;
      List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(renderView,
            renderView.getEntityBoundingBox()
                  .addCoord(lookVec.xCoord * reach, lookVec.yCoord * reach, lookVec.zCoord * reach)
                  .expand(1.0D, 1.0D, 1.0D));
      double closest = reach;

      for (Entity entity : entities) {
         if (entity.canBeCollidedWith()) {
            float ex = (float) entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(ex, ex, ex).expand(expand, expand, expand);
            MovingObjectPosition mop = box.calculateIntercept(eyePos, reachVec);
            if (box.isVecInside(eyePos)) {
               if (0.0D < closest || closest == 0.0D) {
                  target = entity;
                  hitVec = mop == null ? eyePos : mop.hitVec;
                  closest = 0.0D;
               }
            } else if (mop != null) {
               double dist = eyePos.distanceTo(mop.hitVec);
               if (dist < closest || closest == 0.0D) {
                  if (entity == renderView.ridingEntity) {
                     if (closest == 0.0D) {
                        target = entity;
                        hitVec = mop.hitVec;
                     }
                  } else {
                     target = entity;
                     hitVec = mop.hitVec;
                     closest = dist;
                  }
               }
            }
         }
      }

      if (closest < reach && !(target instanceof EntityLivingBase) && !(target instanceof EntityItemFrame)) {
         target = null;
      }

      mc.mcProfiler.endSection();
      if (target != null && hitVec != null) {
         return new Object[] { target, hitVec };
      }
      return null;
   }

   public static Vec3 getVectorForRotation(float pitch, float yaw) {
      float f = (float) Math.cos(-yaw * 0.017453292F - Math.PI);
      float f1 = (float) Math.sin(-yaw * 0.017453292F - Math.PI);
      float f2 = (float) -Math.cos(-pitch * 0.017453292F);
      float f3 = (float) Math.sin(-pitch * 0.017453292F);
      return new Vec3((double) (f1 * f2), (double) f3, (double) (f * f2));
   }
}