package com.rejahtavi.rfp2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

// 音效注册和管理类
public class RFP2Sound {
    // 声明强心针使用音效事件
    public static SoundEvent STIMULANT_USE_SOUND;

    // 注册所有音效
    public static void registerSounds(FMLPreInitializationEvent event) {
        try {
            // 注册强心针使用音效
            ResourceLocation stimulantSoundLoc = new ResourceLocation("rfp2", "use_drug_sound");
            STIMULANT_USE_SOUND = new SoundEvent(stimulantSoundLoc).setRegistryName(stimulantSoundLoc);
            ForgeRegistries.SOUND_EVENTS.register(STIMULANT_USE_SOUND);
            //注册防弹插板使用音效
            ResourceLocation armorPlateSoundLoc = new ResourceLocation("rfp2", "use_armor_plate_sound");
            ARMOR_PLATE_USE_SOUND = new SoundEvent(armorPlateSoundLoc).setRegistryName(armorPlateSoundLoc);
            ForgeRegistries.SOUND_EVENTS.register(ARMOR_PLATE_USE_SOUND);

            event.getModLog().info("RFP2音效注册成功");
        } catch (Exception e) {
            event.getModLog().error("RFP2音效注册失败: " + e.getMessage());
        }
    }

    // 播放强心针使用音效的静态方法
    public static void playStimulantSound(EntityPlayer player) {
        if (STIMULANT_USE_SOUND == null) {
            RFP2.logToChat(TextFormatting.RED + "强心针音效未正确加载");
            return;
        }

        try {
            player.world.playSound(
                    player.posX, player.posY, player.posZ,
                    STIMULANT_USE_SOUND,
                    SoundCategory.PLAYERS,
                    1.0F, // 音量
                    1.0F, // 音调
                    false // 不重复
            );
        } catch (Exception e) {
            RFP2.logToChat(TextFormatting.RED + "播放强心针音效失败");
        }
    }

    // 在现有代码中添加防弹插板使用音效
    public static SoundEvent ARMOR_PLATE_USE_SOUND;



    // 添加播放防弹插板音效的静态方法
    public static void playArmorPlateSound(EntityPlayer player) {
        if (ARMOR_PLATE_USE_SOUND == null) {
            RFP2.logToChat(TextFormatting.RED + "防弹插板音效未正确加载");
            return;
        }

        try {
            player.world.playSound(
                    player.posX, player.posY, player.posZ,
                    ARMOR_PLATE_USE_SOUND,
                    SoundCategory.PLAYERS,
                    1.0F, // 音量
                    1.0F, // 音调
                    false // 不重复
            );
        } catch (Exception e) {
            RFP2.logToChat(TextFormatting.RED + "播放防弹插板音效失败");
        }
    }
}
