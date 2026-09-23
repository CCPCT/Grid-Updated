package de.guntram.mcmod.grid.modConfig;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class ConfigScreen extends Screen {

    public ConfigScreen() {
        super(Component.literal("Grid config"));
    }

    public static Screen getConfigScreen(Screen parent) {
        ModConfig.load();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("General"))
                .setSavingRunnable(ModConfig::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory generalTab = builder.getOrCreateCategory(Component.literal("General"));


        // General settings
        generalTab.addEntry(entryBuilder.startColorField(Component.literal("Block colour"), ModConfig.get().blockColor)
                .setDefaultValue(0x8080ff)
                .setSaveConsumer(newValue -> ModConfig.get().blockColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Component.literal("Line colour"), ModConfig.get().lineColor)
                .setDefaultValue(0xff8000)
                .setSaveConsumer(newValue -> ModConfig.get().lineColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Component.literal("Circle colour"), ModConfig.get().circleColor)
                .setDefaultValue(0x00e480)
                .setSaveConsumer(newValue -> ModConfig.get().circleColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Component.literal("Spawn at night colour"), ModConfig.get().spawnNightColor)
                .setDefaultValue(0xffff00)
                .setSaveConsumer(newValue -> ModConfig.get().spawnNightColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Component.literal("Spawn at day colour"), ModConfig.get().spawnDayColor)
                .setDefaultValue(0xff0000)
                .setSaveConsumer(newValue -> ModConfig.get().spawnDayColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Component.literal("Biome colour"), ModConfig.get().biomeColor)
                .setDefaultValue(0xff00ff)
                .setSaveConsumer(newValue -> ModConfig.get().biomeColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Component.literal("Slime colour"), ModConfig.get().slimeColor)
                .setDefaultValue(0x00ff00)
                .setSaveConsumer(newValue -> ModConfig.get().slimeColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startBooleanToggle(Component.literal("Use cache"), ModConfig.get().useCache)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> ModConfig.get().useCache = newValue)
                .build());


        return builder.build();
    }
}
