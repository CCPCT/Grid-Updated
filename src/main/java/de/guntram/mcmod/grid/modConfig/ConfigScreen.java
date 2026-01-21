package de.guntram.mcmod.grid.modConfig;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {

    public ConfigScreen() {
        super(Text.literal("Grid config"));
    }

    public static Screen getConfigScreen(Screen parent) {
        ModConfig.load();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("General"))
                .setSavingRunnable(ModConfig::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory generalTab = builder.getOrCreateCategory(Text.literal("General"));


        // General settings
        generalTab.addEntry(entryBuilder.startColorField(Text.literal("Block colour"), ModConfig.get().blockColor)
                .setDefaultValue(0x8080ff)
                .setSaveConsumer(newValue -> ModConfig.get().blockColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Text.literal("Line colour"), ModConfig.get().lineColor)
                .setDefaultValue(0xff8000)
                .setSaveConsumer(newValue -> ModConfig.get().lineColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Text.literal("Circle colour"), ModConfig.get().circleColor)
                .setDefaultValue(0x00e480)
                .setSaveConsumer(newValue -> ModConfig.get().circleColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Text.literal("Spawn at night colour"), ModConfig.get().spawnNightColor)
                .setDefaultValue(0xffff00)
                .setSaveConsumer(newValue -> ModConfig.get().spawnNightColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Text.literal("Spawn at day colour"), ModConfig.get().spawnDayColor)
                .setDefaultValue(0xff0000)
                .setSaveConsumer(newValue -> ModConfig.get().spawnDayColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Text.literal("Biome colour"), ModConfig.get().biomeColor)
                .setDefaultValue(0xff00ff)
                .setSaveConsumer(newValue -> ModConfig.get().biomeColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startColorField(Text.literal("Slime colour"), ModConfig.get().slimeColor)
                .setDefaultValue(0x00ff00)
                .setSaveConsumer(newValue -> ModConfig.get().slimeColor = newValue)
                .build());

        generalTab.addEntry(entryBuilder.startBooleanToggle(Text.literal("Use cache"), ModConfig.get().useCache)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> ModConfig.get().useCache = newValue)
                .build());


        return builder.build();
    }
}
