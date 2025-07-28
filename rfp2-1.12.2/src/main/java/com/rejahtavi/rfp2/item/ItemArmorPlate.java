package com.rejahtavi.rfp2.item;

import net.minecraft.item.Item;

import static com.rejahtavi.rfp2.RFP2.TAB_RFP2;

public class ItemArmorPlate extends Item
{
    public static final Item itemArmorPlate = new ItemArmorPlate();

    public ItemArmorPlate()
    {
        super();
        setMaxStackSize(8);
        setCreativeTab(TAB_RFP2);

    }


}
