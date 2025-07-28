package com.rejahtavi.rfp2.item;

import net.minecraft.item.Item;

import static com.rejahtavi.rfp2.RFP2.TAB_RFP2;

public class Itemstimulant extends Item
{
    public static final Item itemstimulant = new Itemstimulant();

    public Itemstimulant()
    {
        super();
        setMaxStackSize(5);
        setCreativeTab(TAB_RFP2);

    }


}
