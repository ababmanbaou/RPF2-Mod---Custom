package com.rejahtavi.rfp2.client;

import com.rejahtavi.rfp2.item.*;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent.Register;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

@Mod.EventBusSubscriber
public class ClientEvent
{
      @SubscribeEvent
    public static void registerItems(Register<Item> event)
      {
         IForgeRegistry<Item> registry = event.getRegistry();

         registry.register(Itemstimulant.itemstimulant.setRegistryName("itemstimulant").setUnlocalizedName("itemstimulant"));
         registry.register(ItemArmorPlate.itemArmorPlate.setRegistryName("itemArmorplate").setUnlocalizedName("itemArmorplate"));

      }

      @SubscribeEvent
      public static void registerModels(ModelRegistryEvent event)
      {
              ModelLoader.setCustomModelResourceLocation(Itemstimulant.itemstimulant, 0, new ModelResourceLocation("rfp2:itemstimulant", "inventory"));
              ModelLoader.setCustomModelResourceLocation(ItemArmorPlate.itemArmorPlate, 0, new ModelResourceLocation("rfp2:itemArmorplate", "inventory"));

      }
}
