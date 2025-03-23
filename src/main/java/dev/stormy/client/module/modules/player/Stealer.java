package dev.stormy.client.module.modules.player;

import dev.stormy.client.module.Module;
import dev.stormy.client.module.setting.impl.DoubleSliderSetting;
import dev.stormy.client.module.setting.impl.TickSetting;
import dev.stormy.client.module.setting.impl.SliderSetting;
import dev.stormy.client.utils.math.MathUtils;
import dev.stormy.client.utils.math.TimerUtils;
import dev.stormy.client.utils.player.ItemUtils;
import me.tryfle.stormy.events.UpdateEvent;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.entity.EntityList;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.weavemc.loader.api.event.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class Stealer extends Module {
    public static DoubleSliderSetting delay;
    public static TickSetting menuCheck, ignoreTrash, autoClose, randomOrder, humanPatterns, pauseRandomly;
    public static SliderSetting pauseChance;
    public static SliderSetting pauseTime;
    public static TickSetting emulateHovering;
    public static SliderSetting hoverTime;
    public static TickSetting smartStealing;

    public Stealer() {
        super("Stealer", Module.ModuleCategory.Player, 0);
        this.registerSetting(delay = new DoubleSliderSetting("Delay", 100, 150, 0, 500, 50));
        this.registerSetting(menuCheck = new TickSetting("Menu Check", true));
        this.registerSetting(ignoreTrash = new TickSetting("Ignore Trash", true));
        this.registerSetting(autoClose = new TickSetting("Auto Close", true));
        this.registerSetting(randomOrder = new TickSetting("Random Order", true));
        this.registerSetting(humanPatterns = new TickSetting("Human Patterns", true));
        this.registerSetting(pauseRandomly = new TickSetting("Random Pauses", false));
        this.registerSetting(pauseChance = new SliderSetting("Pause Chance", 15, 1, 50, 1));
        this.registerSetting(pauseTime = new SliderSetting("Pause Time (ms)", 500, 100, 2000, 100));
        this.registerSetting(emulateHovering = new TickSetting("Emulate Hovering", false));
        this.registerSetting(hoverTime = new SliderSetting("Hover Time (ms)", 200, 50, 500, 25));
        this.registerSetting(smartStealing = new TickSetting("Smart Stealing", true));
    }

    private final TimerUtils stopwatch = new TimerUtils();
    private final TimerUtils pauseTimer = new TimerUtils();
    private final TimerUtils hoverTimer = new TimerUtils();
    private long nextClick;
    private int lastClick;
    private int lastSteal;
    private static boolean userInterface;
    private boolean isPaused = false;
    private boolean isHovering = false;
    private int hoverSlot = -1;
    private final Random random = new Random();
    private List<Integer> stealOrder = new ArrayList<>();
    private int currentStealIndex = 0;

    @SubscribeEvent
    public void onUpdate(UpdateEvent event) {
        if (!event.isPre())
            return;

        if (mc.currentScreen instanceof GuiChest) {
            final ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
            final String containerName = container.getLowerChestInventory().getDisplayName().getUnformattedText();

            if (menuCheck.isToggled() && inGUI()) {
                return;
            }

            // Check if we're in a random pause
            if (isPaused) {
                if (pauseTimer.hasReached(ThreadLocalRandom.current().nextLong(
                        (long) (pauseTime.getInput() * 0.7), (long) (pauseTime.getInput() * 1.3)))) {
                    isPaused = false;
                } else {
                    return;
                }
            }

            // Generate random pause if enabled
            if (pauseRandomly.isToggled() && !isPaused && Math.random() * 100 < pauseChance.getInput()) {
                isPaused = true;
                pauseTimer.reset();
                return;
            }

            // Handle hovering emulation
            if (emulateHovering.isToggled() && isHovering) {
                if (hoverTimer.hasReached(ThreadLocalRandom.current().nextLong(
                        (long) (hoverTime.getInput() * 0.8), (long) (hoverTime.getInput() * 1.2)))) {
                    isHovering = false;
                } else {
                    return;
                }
            }

            // Wait for delay between clicks
            if (!this.stopwatch.hasReached(this.nextClick)) {
                return;
            }

            this.lastSteal++;

            // Initialize steal order if needed
            if (stealOrder.isEmpty() || currentStealIndex >= stealOrder.size()) {
                generateStealOrder(container);
                currentStealIndex = 0;
            }

            // If we still have items to steal
            if (currentStealIndex < stealOrder.size()) {
                int slotId = stealOrder.get(currentStealIndex);
                final ItemStack stack = container.getLowerChestInventory().getStackInSlot(slotId);

                if (stack != null && this.lastSteal > 1) {
                    if (!ignoreTrash.isToggled() || ItemUtils.useful(stack)) {
                        // Emulate hovering before clicking
                        if (emulateHovering.isToggled() && !isHovering) {
                            hoverSlot = slotId;
                            isHovering = true;
                            hoverTimer.reset();
                            return;
                        }

                        // Apply human-like delay variations
                        if (humanPatterns.isToggled()) {
                            float itemRarity = getItemRarity(stack);
                            float delayMod = calculateHumanDelayModifier(itemRarity, stack.stackSize);
                            this.nextClick = Math.round(MathUtils.randomInt(
                                    (int) (delay.getInputMin() * delayMod),
                                    (int) (delay.getInputMax() * delayMod)));
                        } else {
                            this.nextClick = Math.round(MathUtils.randomInt(
                                    (int) delay.getInputMin(),
                                    (int) delay.getInputMax()));
                        }

                        // Click to steal the item
                        mc.playerController.windowClick(container.windowId, slotId, 0, 1, mc.thePlayer);
                        this.stopwatch.reset();
                        this.lastClick = 0;
                        currentStealIndex++;
                        return;
                    } else {
                        // Skip trash items
                        currentStealIndex++;
                    }
                } else {
                    currentStealIndex++;
                }
            } else {
                // We've gone through all slots
                this.lastClick++;
                if (this.lastClick > 1 && autoClose.isToggled()) {
                    mc.thePlayer.closeScreen();
                }
            }
        } else {
            this.lastClick = 0;
            this.lastSteal = 0;
            this.stealOrder.clear();
            this.currentStealIndex = 0;
            this.isPaused = false;
            this.isHovering = false;
        }
    }

    private void generateStealOrder(ContainerChest container) {
        stealOrder.clear();

        // Collect all valid slots
        for (int i = 0; i < container.getLowerChestInventory().getSizeInventory(); i++) {
            final ItemStack stack = container.getLowerChestInventory().getStackInSlot(i);
            if (stack != null) {
                stealOrder.add(i);
            }
        }

        // Randomize the order if enabled
        if (randomOrder.isToggled()) {
            if (smartStealing.isToggled()) {
                smartRandomize(container);
            } else {
                Collections.shuffle(stealOrder);
            }
        }
    }

    // Smart randomization that creates a more human-like pattern
    private void smartRandomize(ContainerChest container) {
        List<Integer> newOrder = new ArrayList<>();
        List<Integer> highValue = new ArrayList<>();
        List<Integer> mediumValue = new ArrayList<>();
        List<Integer> lowValue = new ArrayList<>();

        // Categorize items by estimated value
        for (int i : stealOrder) {
            ItemStack stack = container.getLowerChestInventory().getStackInSlot(i);
            float rarity = getItemRarity(stack);

            if (rarity > 0.7f) {
                highValue.add(i);
            } else if (rarity > 0.4f) {
                mediumValue.add(i);
            } else {
                lowValue.add(i);
            }
        }

        // Shuffle each category
        Collections.shuffle(highValue);
        Collections.shuffle(mediumValue);
        Collections.shuffle(lowValue);

        // Humans tend to grab valuable items first, but not perfectly in order
        newOrder.addAll(highValue);
        newOrder.addAll(mediumValue);
        newOrder.addAll(lowValue);

        // Add some truly random behavior by potentially swapping some items
        for (int i = 0; i < newOrder.size(); i++) {
            if (random.nextInt(100) < 30) { // 30% chance to swap
                int swapWith = random.nextInt(newOrder.size());
                int temp = newOrder.get(i);
                newOrder.set(i, newOrder.get(swapWith));
                newOrder.set(swapWith, temp);
            }
        }

        stealOrder = newOrder;
    }

    private float getItemRarity(ItemStack stack) {
        // Basic rarity estimation based on item name and properties
        String name = stack.getUnlocalizedName().toLowerCase();

        float rarity = 0.0f;

        // Assign values based on material type
        if (name.contains("diamond"))
            rarity += 0.9f;
        else if (name.contains("emerald"))
            rarity += 0.85f;
        else if (name.contains("gold") || name.contains("golden"))
            rarity += 0.7f;
        else if (name.contains("iron"))
            rarity += 0.6f;
        else if (name.contains("stone"))
            rarity += 0.3f;
        else if (name.contains("wood") || name.contains("wooden"))
            rarity += 0.2f;

        // Higher value for special items
        if (stack.isItemEnchanted())
            rarity += 0.3f;
        if (name.contains("sword") || name.contains("bow"))
            rarity += 0.2f;
        if (name.contains("armor") || name.contains("helmet") ||
                name.contains("chestplate") || name.contains("leggings") ||
                name.contains("boots"))
            rarity += 0.15f;

        return Math.min(1.0f, rarity);
    }

    private float calculateHumanDelayModifier(float itemRarity, int stackSize) {
        // Humans grab valuable items faster, and large stacks slightly faster
        float rarityMod = (itemRarity > 0.7f) ? 0.8f : (itemRarity > 0.4f) ? 1.0f : 1.2f;
        float stackMod = 1.0f - (Math.min(stackSize, 32) / 64.0f * 0.2f); // 0-20% reduction based on stack size

        // Add some randomness
        float randomFactor = 0.9f + (random.nextFloat() * 0.2f); // 0.9-1.1

        return rarityMod * stackMod * randomFactor;
    }

    @SubscribeEvent
    public void onUpdate2(UpdateEvent event) {
        if (!event.isPre())
            return;

        userInterface = false;

        if (mc.currentScreen instanceof GuiChest) {
            final ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;

            int confidence = 0, totalSlots = 0, amount = 0;

            for (final Slot slot : container.inventorySlots) {
                if (slot.getHasStack() && amount++ <= 26) {
                    final ItemStack itemStack = slot.getStack();

                    if (itemStack == null) {
                        continue;
                    }

                    final String name = itemStack.getDisplayName();
                    final String expectedName = expectedName(itemStack);
                    final String strippedName = name.toLowerCase().replace(" ", "");
                    final String strippedExpectedName = expectedName.toLowerCase().replace(" ", "");

                    if (strippedName.contains(strippedExpectedName)) {
                        confidence -= 0.1;
                    } else {
                        confidence++;
                    }

                    totalSlots++;
                }
            }

            userInterface = (float) confidence / (float) totalSlots > 0.5f;
        }
    }

    public static boolean inGUI() {
        return userInterface;
    }

    private String expectedName(final ItemStack stack) {
        String s = (StatCollector.translateToLocal(stack.getUnlocalizedName() + ".name")).trim();
        final String s1 = EntityList.getStringFromID(stack.getMetadata());

        if (s1 != null) {
            s = s + " " + StatCollector.translateToLocal("entity." + s1 + ".name");
        }

        return s;
    }
}