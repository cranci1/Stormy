package me.sassan.base.api.module;

import me.sassan.base.impl.module.combact.*;
import me.sassan.base.impl.module.player.*;
import me.sassan.base.impl.module.render.*;
import net.minecraft.client.Minecraft;
import net.weavemc.loader.api.event.EventBus;
import net.weavemc.loader.api.event.KeyboardEvent;

import java.util.*;

/**
 * @author sassan
 *         18.11.2023, 2023
 */
public class ModuleRepo {
    public List<Module> list = new ArrayList<>();

    public ModuleRepo() {
        list.add(new Jump());
        list.add(new AutoClicker());
        list.add(new ClickGui());

        EventBus.subscribe(KeyboardEvent.class, event -> {
            for (Module m : list) {
                if (event.getKeyState())
                    return;

                if (event.getKeyCode() == m.getKey()) {
                    m.toggle();
                    Minecraft.getMinecraft().thePlayer.addChatMessage(
                            new net.minecraft.util.ChatComponentText(
                                    "Toggled " + m.getName() + " to " + m.isEnabled()));
                }
            }
        });

        System.out.println("Loaded " + list.size() + " modules");
    }
}
