package com.rejahtavi.rfp2;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.regex.PatternSyntaxException;

import com.rejahtavi.rfp2.item.Itemstimulant;
import com.rejahtavi.rfp2.network.ArmorPlateConsumeMessage;
import com.rejahtavi.rfp2.network.StimulantConsumeMessage;
import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import net.minecraft.util.text.TextFormatting;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.imageio.ImageIO;

import static com.rejahtavi.rfp2.RFP2Sound.playStimulantSound;



/* 
 * This class receives and processes all events related to the player dummy.
 * It is also responsible for processing events related to keybinds and configuration.
 */

// register this class as an event handler with forge
@Mod.EventBusSubscriber(Side.CLIENT)
public class RFP2State
{
    // Local objects to track mod internal state
    
    // handle to the player dummy entity
    EntityPlayerDummy dummy;
    
    // timers for performance waits
    int  spawnDelay;
    long checkEnableModDelay;
    long checkEnableRealArmsDelay;
    int  suspendApiDelay;
    
    // state flags
    boolean lastActivateCheckResult;
    boolean lastRealArmsCheckResult;
    boolean enableMod;
    boolean enableRealArms;
    boolean enableHeadTurning;
    boolean enableStatusMessages;
    boolean conflictsDetected = false;
    boolean conflictCheckDone = false;
    //使用强心针的相关变量
    private int stimulantTimer = 0;
    private boolean usingStimulant = false;
    private boolean wasRealArmsEnabled = false;
    private int screenFlashTimer = 0;
    private int delayedEffectTimer = 0;
    private boolean shouldTriggerDelayedEffects = false;
    // 泛白效果计时器
    private static final int FLASH_TOTAL_TICKS = 10; // 总时长500ms
    private static final int FADE_IN_TICKS = 3;     // 淡入3tick
    private static final int HOLD_TICKS = 4;        // 保持4tick
    private static final int FADE_OUT_TICKS = 3;    // 淡出3tick
    //增加冷却时间
    private int stimulantCooldown = 0;
    private static final int COOLDOWN_TICKS = 40; // 2秒(20ticks/秒)


    // 静态图片相关变量
    private static int flashTextureId = -1; // 用int存储纹理ID，而非TextureObject

    // 存储纹理尺寸的变量
    private static int textureWidth;
    private static int textureHeight;

    // 添加防弹插板相关变量
    private int armorPlateTimer = 0;
    private boolean usingArmorPlate = false;
    private boolean wasRealArmsEnabledForPlate = false;
    private static final int ARMOR_PLATE_FIRST_DELAY = 62; // 62 tick后执行第一步
    private static final int ARMOR_PLATE_SECOND_DELAY = 100; // 82 tick后执行第二步


    
    // Constructor
    public RFP2State()
    {
        // No dummy exists at startup
        dummy = null;
        
        // Start a timer so that we wait a bit for things to load before first trying to spawn the dummy
        spawnDelay = RFP2.DUMMY_MIN_RESPAWN_INTERVAL;
        
        // Initialize local variables
        checkEnableModDelay      = 0;
        checkEnableRealArmsDelay = 0;
        suspendApiDelay          = 0;
        lastActivateCheckResult  = true;
        lastRealArmsCheckResult  = true;
        
        // Import initial state from config file
        enableMod            = RFP2Config.preferences.enableMod;
        enableRealArms       = RFP2Config.preferences.enableRealArms;
        enableHeadTurning    = RFP2Config.preferences.enableHeadTurning;
        enableStatusMessages = RFP2Config.preferences.enableStatusMessages;
        
        // Register ourselves on the bus so we can receive and process events
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    // Receive key press events for key binding handling
    @SubscribeEvent(
        priority = EventPriority.NORMAL,
        receiveCanceled = true)
    public void onEvent(KeyInputEvent event)
    {
        // DISABLED for 1.3.1 -- now only warns players 
        // kill mod completely when a conflict is detected.
        // if (this.conflictsDetected) return;
        
        // Check key binding in turn for new presses
        if (RFP2.keybindArmsToggle.checkForNewPress())
        {
            enableRealArms = !enableRealArms;
            if (enableStatusMessages)
            {
                // log keybind-triggered state changes to chat if configured to do so
                RFP2.logToChat(RFP2.MODNAME + " arms " + (enableRealArms ? TextFormatting.GREEN + "enabled" : TextFormatting.RED + "disabled"));
            }
        }
        // Check key binding in turn for new presses
        if (RFP2.keybindModToggle.checkForNewPress())
        {
            enableMod = !enableMod;
            if (enableStatusMessages)
            {
                // log keybind-triggered state changes to chat if configured to do so
                RFP2.logToChat(RFP2.MODNAME + " mod " + (enableMod ? TextFormatting.GREEN + "enabled" : TextFormatting.RED + "disabled"));
            }
        }
        // Check key binding in turn for new presses
        if (RFP2.keybindHeadRotationToggle.checkForNewPress())
        {
            enableHeadTurning = !enableHeadTurning;
            if (enableStatusMessages)
            {
                // log keybind-triggered state changes to chat if configured to do so
                RFP2.logToChat(RFP2.MODNAME + " head rotation " + (enableHeadTurning ? TextFormatting.GREEN + "enabled" : TextFormatting.RED + "disabled"));
            }
        }
        // 处理强心针使用
        if (RFP2.keybindUseStimulant.checkForNewPress()) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null) {
                useStimulant(player);
            }
        }
        if (RFP2.keybindUseArmorPlate.checkForNewPress()) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null) {
                useArmorPlate(player);
            }
        }
    }
    // 添加强心针使用逻辑
    private void useStimulant(EntityPlayer player)
    {
        // 检查冷却
        if (stimulantCooldown > 0) {
            int secondsLeft = (stimulantCooldown + 19) / 20;
            RFP2.logToChat(TextFormatting.RED + "强心针冷却中，还剩" + secondsLeft + "秒");
            return;
        }
        // 检查是否正在使用中或正在使用防弹插板
        if (usingStimulant || usingArmorPlate) {
            RFP2.logToChat(TextFormatting.RED + "无法同时使用防弹插板和强心针");
            return;
        }

        // 客户端只发送请求，不做任何预消耗或视觉效果
        RFP2.NETWORK.sendToServer(new StimulantConsumeMessage());
    }
    // 添加使用防弹插板的方法
    private void useArmorPlate(EntityPlayer player) {
        // 检查是否正在使用中或正在使用强心针
        if (usingArmorPlate || usingStimulant) {
            RFP2.logToChat(TextFormatting.RED + "无法同时使用防弹插板和强心针");
            return;
        }

        // 客户端只发送请求，不做任何预消耗或视觉效果
        RFP2.NETWORK.sendToServer(new ArmorPlateConsumeMessage());
    }

    // 新增处理服务器成功回执的方法强心针
    public void handleStimulantSuccess() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        // 服务器已确认消耗，执行视觉效果和冷却
        stimulantCooldown = COOLDOWN_TICKS;
        wasRealArmsEnabled = enableRealArms;
        if (enableRealArms) {
            sendCommand(player, "cpm animate @s 打血");
        } else {
            enableRealArms = true;
            sendCommand(player, "cpm animate @s 打血");
        }
        usingStimulant = true;
        stimulantTimer = 0;
        shouldTriggerDelayedEffects = true;
        delayedEffectTimer = 0;

    }

    // 处理服务器成功回执的方法防弹插片
    public void handleArmorPlateSuccess() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        // 播放使用音效
        RFP2Sound.playArmorPlateSound(player);

        // 记录当前手臂渲染状态
        boolean isRealArmsEnabled = enableRealArms;
        wasRealArmsEnabledForPlate = isRealArmsEnabled;

        if (isRealArmsEnabled) { // 手臂渲染开启
            // 输出指令
            sendCommand(player, "cpm animate @s 打甲");
        } else { // 手臂渲染关闭
            // 开启手臂渲染
            enableRealArms = true;
            // 输出指令
            sendCommand(player, "cpm animate @s 打甲");
        }

        // 开始计时
        usingArmorPlate = true;
        armorPlateTimer = 0;
    }


    // 服务器端（含单机内置服务器）消耗逻辑强心针
    private boolean consumeStimulantServer(EntityPlayer player) {
        // 检查主手
        ItemStack mainHand = player.getHeldItemMainhand();
        if (mainHand.getItem() == Itemstimulant.itemstimulant) {
            mainHand.shrink(1);
            player.inventory.markDirty();
            return true;
        }

        // 检查副手
        ItemStack offHand = player.getHeldItemOffhand();
        if (offHand.getItem() == Itemstimulant.itemstimulant) {
            offHand.shrink(1);
            player.inventory.markDirty();
            return true;
        }

        // 检查主物品栏
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack stack = player.inventory.mainInventory.get(i);
            if (stack.getItem() == Itemstimulant.itemstimulant) {
                stack.shrink(1);
                player.inventory.markDirty();
                return true;
            }
        }

        // 检查副手物品栏
        for (int i = 0; i < player.inventory.offHandInventory.size(); i++) {
            ItemStack stack = player.inventory.offHandInventory.get(i);
            if (stack.getItem() == Itemstimulant.itemstimulant) {
                stack.shrink(1);
                player.inventory.markDirty();
                return true;
            }
        }

        return false;
    }





    // 检查玩家是否有强心针
    private boolean hasStimulant(EntityPlayer player)
    {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack.getItem() == Itemstimulant.itemstimulant) {
                return true;
            }

        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (stack.getItem() == Itemstimulant.itemstimulant) {
                return true;
            }
        }
        return false;
    }

    // 添加一个更新冷却的方法，并在事件中调用
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // 更新冷却计时器
            if (stimulantCooldown > 0) {
                stimulantCooldown--;
            }
        }
        // 处理防弹插板计时器
        if (usingArmorPlate) {
            armorPlateTimer++;

            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null) {
                usingArmorPlate = false;
                return;
            }

            // 60 tick时添加伤害吸收效果
            if (armorPlateTimer == ARMOR_PLATE_FIRST_DELAY) {
                sendCommand(player, "effect @s absorption 999999 8");
                sendCommand(player, "effect @s resistance 3 4");
            }

            // 100 tick时（如果初始状态是关闭手臂渲染）关闭手臂渲染
            if (armorPlateTimer == ARMOR_PLATE_SECOND_DELAY && !wasRealArmsEnabledForPlate) {
                enableRealArms = false;
            }

            // 100 tick时结束流程，发送最终指令
            if (armorPlateTimer >= ARMOR_PLATE_SECOND_DELAY) {
                sendCommand(player, "cpm animate @s 打甲");
                usingArmorPlate = false;
                armorPlateTimer = 0;
            }
        }

    }
    // 发送命令——这段代码的主要功能是通过当前玩家实例向 Minecraft 游戏发送一条命令。具体来说，它会构造一个命令字符串，
    // 然后通过 sendChatMessage 方法发送这个命令，最终由游戏执行。这里的命令可能是任何有效的 Minecraft 命令，
    // 具体取决于 command 变量的内容。

    private void sendCommand(EntityPlayer player, String command) {
        Minecraft.getMinecraft().player.sendChatMessage("/" + command);
    }
    // returns true when mod conflicts are detected
    public void detectModConflicts(EntityPlayer player)
    {
        // Only let this routine run once per startup
        if (!this.conflictCheckDone)
        {
            // Check for conflicting mods
            String modConflictList = "";
            for (String conflictingID : RFP2.CONFLICT_MODIDS)
            {
                if (Loader.isModLoaded(conflictingID))
                {
                    if (modConflictList.length() != 0) modConflictList += ", ";
                    modConflictList += conflictingID;
                }
            }
            
            // See if we got any hits
            if (modConflictList.length() != 0)
            {
                // If mod compatibility alerts are disabled, JUST put info in the log file.
                if (RFP2Config.compatibility.disableModCompatibilityAlerts)
                {
                    RFP2.logger.log(RFP2.LOGGING_LEVEL_HIGH, this.getClass().getName() + ": WARNING: In-game compatibility alerts have been disabled!");
                    // this.enableMod unchanged
                    // this.disabledForConflict unchanged
                }
                else
                {
                    // Mod compatibility alerts are enabled -- warn the player via in-game chat that something is amiss
                    //@formatter:off
                    RFP2.logToChatByPlayer("" + TextFormatting.BOLD + TextFormatting.GOLD
                                           + "WARNING: RFP2 has known compatibility issues with the mod(s): "
                                           + TextFormatting.RESET + TextFormatting.RED
                                           + modConflictList + ".", player);

                    RFP2.logToChatByPlayer("" + TextFormatting.BOLD + TextFormatting.GOLD
                                           + "Be aware that visual glitches may occur.", player);
                    
                    RFP2.logToChatByPlayer("Press the hotkey (Default: Apostrophe) to use RFP2 anyway.", player);
                                        
                    RFP2.logToChatByPlayer("" + TextFormatting.RESET + TextFormatting.GRAY
                                           + "(You can disable this warning in mod options.)", player);
                    //@formatter:on                    
                    this.conflictsDetected = true;
                    this.enableMod         = false;
                }
                RFP2.logger.log(RFP2.LOGGING_LEVEL_HIGH, this.getClass().getName() + ": WARNING: Detected conflicting mod(s): " + modConflictList);
            }
            this.conflictCheckDone = true;
        }
    }


    
    // Receive event when player hands are about to be drawn
    @SubscribeEvent(
        priority = EventPriority.HIGHEST)
    public void onEvent(RenderHandEvent event)
    {
        // DISABLED for 1.3.1 -- now only warns players 
        // kill mod completely when a conflict is detected.
        // if (this.conflictsDetected) return;
        
        // Get local player reference
        EntityPlayer player = Minecraft.getMinecraft().player;
        // if: 1) player exists AND 2) mod is active AND 3) rendering real arms is active
        if (player != null && RFP2.state.isModEnabled(player) && RFP2.state.isRealArmsEnabled(player))
        {
            // then skip drawing the vanilla 2D HUD arms by canceling the event
            event.setCanceled(true);
        }
        if (RFP2.keybindUseArmorPlate.checkForNewPress()) {
            player = Minecraft.getMinecraft().player;
            if (player != null) {
                useArmorPlate(player);
            }
        }

// 强心针的按键处理
        if (RFP2.keybindUseStimulant.checkForNewPress()) {
            player = Minecraft.getMinecraft().player;
            if (player != null) {
                useStimulant(player);
            }
        }
    }
    
    // Receive the main game tick event
    @SubscribeEvent
    public void onEvent(TickEvent.ClientTickEvent event)
    {
        // DISABLED for 1.3.1 -- now only warns players 
        // kill mod completely when a conflict is detected.
        // if (this.conflictsDetected) return;
        
        // Make this block as fail-safe as possible, since it runs every tick
        try
        {
            // Decrement timers
            if (checkEnableModDelay > 0) --checkEnableModDelay;
            if (checkEnableRealArmsDelay > 0) --checkEnableRealArmsDelay;
            if (suspendApiDelay > 0) --suspendApiDelay;
            
            // Get player reference and null check it
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null)
            {
                // Check if dummy needs to be spawned
                if (dummy == null)
                {
                    // It does, are we in a respawn waiting interval?
                    if (spawnDelay > 0)
                    {
                        // Yes, we are still waiting; is the mod enabled?
                        if (enableMod)
                        {
                            // Yes, the mod is enabled and we are waiting: decrement the counter
                            --spawnDelay;
                        }
                        else
                        {
                            // No, the mod is not enabled, and we are waiting:
                            // Hold the timer at full so that the delay works when the mod is turned back on
                            spawnDelay = RFP2.DUMMY_MIN_RESPAWN_INTERVAL;
                        }
                    }
                    else
                    {
                        // No, the spawn timer has expired: Go ahead and try to spawn the dummy.
                        attemptDummySpawn(player);
                    }
                }
                // The dummy already exists, let's check up on it
                else
                {
                    // Track whether we need to reset the existing dummy.
                    // We should only reset it ONCE, even if multiple reasons are true.
                    // (otherwise we will not be able to log the remaining reasons after it is reset)
                    // This is done this way to ease future troubleshooting.
                    boolean needsReset = false;
                    
                    // Did the player change dimensions on us? If so, reset the dummy.
                    if (dummy.world.provider.getDimension() != player.world.provider.getDimension())
                    {
                        needsReset = true;
                        RFP2.logger.log(RFP2.LOGGING_LEVEL_DEBUG,
                                        this.getClass().getName() + ": Respawning dummy because player changed dimension.");
                    }
                    
                    // Did the player teleport, move too fast, or somehow else get separated? If so, reset the dummy.
                    if (dummy.getDistanceSq(player) > RFP2.DUMMY_MAX_SEPARATION)
                    {
                        needsReset = true;
                        RFP2.logger.log(RFP2.LOGGING_LEVEL_DEBUG,
                                        this.getClass().getName() + ": Respawning dummy because player and dummy became separated.");
                    }
                    
                    // Has it been excessively long since we last updated the dummy's state? (perhaps due to lag?)
                    if (dummy.lastTickUpdated < player.world.getTotalWorldTime() - RFP2.DUMMY_UPDATE_TIMEOUT)
                    {
                        needsReset = true;
                        RFP2.logger.log(RFP2.LOGGING_LEVEL_DEBUG,
                                        this.getClass().getName() + ": Respawning dummy because state became stale. (Is the server lagging?)");
                    }
                    
                    // Did one of the above checks necessitate a reset?
                    if (needsReset)
                    {
                        // Yes, proceed with the reset.
                        resetDummy();
                    }
                }
            }
        }
        catch (Exception e)
        {
            // If anything goes wrong, shut the mod off and write an error to the logs.
            RFP2.errorDisableMod(this.getClass().getName() + ".onEvent(TickEvent.ClientTickEvent)", e);
        }
        if (shouldTriggerDelayedEffects) {
            delayedEffectTimer++;

            // 2tick后触发音效
            if (delayedEffectTimer == 2) {
                EntityPlayer player = Minecraft.getMinecraft().player;
                if (player != null) {
                    // 播放延迟的音效
                    playStimulantSound(player);
                }
                // 重置延迟标记（如果不需要在这里重置，可以移除这行）
                // shouldTriggerDelayedEffects = false;
            }

            // 20tick后触发泛白效果
            if (delayedEffectTimer == 20) {
                // 在这里才设置泛白计时器，使其开始生效
                screenFlashTimer = 10; // 泛白持续10tick,相关变量见76行-80行
                // 重置延迟标记
                shouldTriggerDelayedEffects = false;
            }
        }


        // 处理强心针效果
        if (usingStimulant) {
            stimulantTimer++;

            // 0.94秒(18ticks)时
            if (stimulantTimer == 18) {
                EntityPlayer player = Minecraft.getMinecraft().player;
                if (player != null) {
                    sendCommand(player, "effect @s regeneration 1 250");
                    sendCommand(player, "effect @s speed 7 1");
                }
            }
            // 状态0时，2秒(40ticks)关闭手臂渲染
            if (!wasRealArmsEnabled && stimulantTimer == 40) {
                enableRealArms = false;
            }

            // 结束动画
            if (stimulantTimer >= 40) {
                EntityPlayer player = Minecraft.getMinecraft().player;
                if (player != null) {
                    sendCommand(player, "cpm animate @s 打血");
                }
                usingStimulant = false;
            }
        }



    }

    // Handles dummy spawning
    void attemptDummySpawn(EntityPlayer player)
    {
        // Only runs once per startup. Running it here at dummy spawn is the easiest way to ensure it only happens after everything is fully loaded.
        detectModConflicts(player);
        
        try
        {
            // Make sure any existing dummy is dead
            if (dummy != null) dummy.setDead();
            
            // Attempt to spawn a new one at the player's current position
            dummy = new EntityPlayerDummy(player.world);
            dummy.setPositionAndRotation(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
            player.world.spawnEntity(dummy);
        }
        catch (Exception e)
        {
            /*
             * Something went wrong trying to spawn the dummy!
             * We need to write a log entry and reschedule to try again later.
             * 
             * Note that because this code is protected against running too much by a respawn timer,
             * we do not call errorDisableMod() when encountering this error.
             * 
             * Should anything unexpected occur in the spawning, there is a good chance that it will
             * work itself out within a respawn delay or two.
             */
            RFP2.logger.log(RFP2.LOGGING_LEVEL_MED, this.getClass().getName() + ": failed to spawn PlayerDummy! Will retry. Exception:", e.toString());
            e.printStackTrace();
            resetDummy();
        }
    }
    
    // Handles killing off defunct dummies and scheduling respawns
    void resetDummy()
    {
        // If the existing dummy isn't dead, kill it before freeing the reference
        if (dummy != null) dummy.setDead();
        dummy = null;
        
        // DISABLED for 1.3.1 -- now only warns players 
        // kill mod completely when a conflict is detected.
        // if (this.conflictsDetected) return;
        
        // Set timer to spawn a new one
        spawnDelay = RFP2.DUMMY_MIN_RESPAWN_INTERVAL;
    }
    
    public void setSuspendTimer(int ticks)
    {
        // DISABLED for 1.3.1 -- now only warns players 
        // kill mod completely when a conflict is detected.
        // if (this.conflictsDetected) return;
        
        // check if tick value is valid; invalid values will be ignored
        if (ticks > 0 && ticks <= RFP2.MAX_SUSPEND_TIMER)
        {
            // Only allow increasing the timer externally
            //  * This is so multiple mods can use the API concurrently, and RFP2 being suspended is the preferred state.
            //  * Once all mods stop requesting suspension times, the timer will expire at the longest, last value requested.
            if (ticks > suspendApiDelay) suspendApiDelay = ticks;
        }
    }
    
    // Check if mod should be disabled for any reason
    public boolean isModEnabled(EntityPlayer player)
    {
        // DISABLED for 1.3.1 -- now only warns players 
        // kill mod completely when a conflict is detected.
        // if (this.conflictsDetected) return;
        
        // No need to check anything if we are configured to be disabled
        if (!enableMod) return false;
        
        // Don't do anything if we've been suspended by another mod
        if (suspendApiDelay > 0) return false;
        
        // No need to check anything else if player is dead or otherwise cannot be found
        if (player == null) return false;
        
        // No need to check anything else if dummy is dead or otherwise cannot be found
        if (dummy == null) return false;
        
        /* 
         * Only check the player's riding status if we haven't recently.
         * This saves on performance -- it is not necessary to check this list on every single frame!
         * Once every few ticks is more than enough to remain invisible to the player.
         * Keep in mind that "every few ticks", or several 20ths of a second,
         * could be tens of frames where we skip the check with a good GPU!
         */
        if (checkEnableModDelay == 0)
        {
            // The timer has expired, we need to run the checks
            
            // reset timer
            checkEnableModDelay = RFP2.MIN_ACTIVATION_CHECK_INTERVAL;
            
            // Implement swimming check functionality
            if (RFP2Config.compatibility.disableWhenSwimming && dummy.isSwimming())
            {
                // we are swimming and are configured to disable when this is true, so we are disabled
                lastActivateCheckResult = false;
            }
            else
            {
                // we are not swimming, or that check is disabled. proceed to the mount check
                
                // get a reference to the player's mount, if it exists
                Entity playerMountEntity = player.getRidingEntity();
                if (playerMountEntity == null)
                {
                    // Player isn't riding, so we are enabled.
                    lastActivateCheckResult = true;
                }
                else
                {
                    // Player is riding something, find out what it is and if it's on our conflict list
                    if (stringMatchesRegexList(playerMountEntity.getName().toLowerCase(), RFP2Config.compatibility.mountConflictList))
                    {
                        // player is riding a conflicting entity, so we are disabled.
                        lastActivateCheckResult = false;
                    }
                    else
                    {
                        // No conflicts found, so we are enabled.
                        lastActivateCheckResult = true;
                    }
                }
            }
            
        }
        return lastActivateCheckResult;
    }
    
    // Check if we should render real arms or not
    public boolean isRealArmsEnabled(EntityPlayer player)
    {
        // DISABLED for 1.3.1 -- now only warns players 
        // kill mod completely when a conflict is detected.
        // if (this.conflictsDetected) return;
        
        // No need to check anything if we don't want this enabled
        if (!enableRealArms) return false;
        
        // No need to check anything if player is dead
        if (player == null) return false;
        
        // only run the inventory check if we haven't done it recently
        // once per tick is enough -- isRealArmsEnabled might be called many times per tick!
        if (checkEnableRealArmsDelay == 0)
        {
            // need to check the player's inventory after all
            // reset the check timer
            checkEnableRealArmsDelay = RFP2.MIN_REAL_ARMS_CHECK_INTERVAL;
            
            // get the names of the player's currently held items
            String itemMainHand = player.inventory.getCurrentItem().getItem().getRegistryName().toString().toLowerCase();
            String itemOffHand  = player.inventory.offHandInventory.get(0).getItem().getRegistryName().toString().toLowerCase();
            
            // Modify the check logic based on whether the "any item" flag is set or not
            if (RFP2Config.compatibility.disableArmsWhenAnyItemHeld)
            {
                // "any item held" behavior is enabled; check if player's hands are empty
                if (itemMainHand.equals("minecraft:air") && itemOffHand.equals("minecraft:air"))
                {
                    // player is not holding anything; enable arm rendering
                    lastRealArmsCheckResult = true;
                }
                else
                {
                    // player is holding something; disable arm rendering
                    lastRealArmsCheckResult = false;
                }
            }
            else
            {
                // The "any item" option is not in use, so we need to check the registry names of any
                // held items against the conflict list
                if (stringMatchesRegexList(itemMainHand, RFP2Config.compatibility.heldItemConflictList)
                    || (stringMatchesRegexList(itemOffHand, RFP2Config.compatibility.heldItemConflictList)))
                {
                    // player is holding a conflicting item in main or off hand; disable arm rendering
                    lastRealArmsCheckResult = false;
                }
                else
                {
                    // no conflicts found; enable arm rendering
                    lastRealArmsCheckResult = true;
                }
            }
        }
        return lastRealArmsCheckResult;
    }
    
    // Check if head rotation is enabled
    public boolean isHeadRotationEnabled(EntityPlayer player)
    {
        // DISABLED for 1.3.1 -- now only warns players 
        // kill mod completely when a conflict is detected.
        // if (this.conflictsDetected) return;
        
        return enableHeadTurning;
    }
    
    // Check a string against a list of regexes and return true if any of them match
    boolean stringMatchesRegexList(String string, String[] regexes)
    {
        // Loop through regex array
        for (String i : regexes)
        {
            // Handle errors due to bad regex syntax entered by user
            try
            {
                // Check if the provided string matches the regex
                if (string.matches(i))
                {
                    // Found a hit, return true
                    return true;
                }
            }
            catch (PatternSyntaxException e)
            {
                // Something is wrong with the regex, switch off the mod and notify the user
                enableMod = false;
                RFP2.logToChat(RFP2.MODNAME + " " + TextFormatting.RED + "Warning: [ " + i + " ] is not a valid regex, please edit your configuration.");
                RFP2.logToChat(RFP2.MODNAME + " mod " + TextFormatting.RED + " disabled");
                return false;
            }
        }

        // Got through the whole array without a hit; return false
        return false;
    }

    // 适用于静态图片的绘制方法
    private static void drawScaledTexturedModalRect(int x, int y, int u, int v, int width, int height,
                                                    int textureWidth, int textureHeight) {
        float f = 1.0F / textureWidth;
        float f1 = 1.0F / textureHeight;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        bufferbuilder.pos(x, y + height, 0.0D)
                .tex(u * f, (v + textureHeight) * f1)
                .color(255, 255, 255, 255).endVertex();
        bufferbuilder.pos(x + width, y + height, 0.0D)
                .tex((u + textureWidth) * f, (v + textureHeight) * f1)
                .color(255, 255, 255, 255).endVertex();
        bufferbuilder.pos(x + width, y, 0.0D)
                .tex((u + textureWidth) * f, v * f1)
                .color(255, 255, 255, 255).endVertex();
        bufferbuilder.pos(x, y, 0.0D)
                .tex(u * f, v * f1)
                .color(255, 255, 255, 255).endVertex();
        tessellator.draw();
    }
//空方法
    public void handleStimulantFailure() {
    }

    // 渲染处理器 - 修正所有方法引用问题
    @Mod.EventBusSubscriber(Side.CLIENT)
    public static class RenderHandler {
        @SubscribeEvent
        public static void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
            if (event.getType() == RenderGameOverlayEvent.ElementType.ALL &&
                    RFP2.state.screenFlashTimer > 0) {

                Minecraft mc = Minecraft.getMinecraft();
                ScaledResolution res = event.getResolution();
                int screenWidth = res.getScaledWidth();
                int screenHeight = res.getScaledHeight();
                float alpha = calculateFlashAlpha(RFP2.state.screenFlashTimer);

                // 确保静态纹理已加载
                if (flashTextureId == -1) {
                    preloadStaticFlashTexture(mc);
                    if (flashTextureId == -1) {
                        renderFallbackWhiteOverlay(mc, screenWidth, screenHeight, alpha);
                        RFP2.state.screenFlashTimer--;
                        return;
                    }
                }

                // 绘制图片覆盖层
                GlStateManager.disableDepth();
                GlStateManager.depthMask(false);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO
                );

                // 修正纹理绑定方式：使用int纹理ID
                TextureManager textureManager = mc.getTextureManager();
                ResourceLocation flashTextureLocation= new ResourceLocation("rfp2", "textures/gui/flash_overlay.png");
                textureManager.bindTexture(flashTextureLocation);  // 这里是关键修复

                GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);

                // 绘制自适应拉伸的静态图片
                drawScaledTexturedModalRect(0, 0, 0, 0,
                        screenWidth, screenHeight,
                        textureWidth, textureHeight);

                // 恢复渲染状态
                GlStateManager.disableBlend();
                GlStateManager.depthMask(true);
                GlStateManager.enableDepth();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

                RFP2.state.screenFlashTimer--;
            }
        }

        // 静态图片加载方法 - 改为public static以便内部类访问
        public static void preloadStaticFlashTexture(Minecraft mc) {
            try {
                ResourceLocation textureLoc = new ResourceLocation("rfp2", "textures/gui/flash_overlay.png");
                IResource resource = mc.getResourceManager().getResource(textureLoc);
                BufferedImage image = ImageIO.read(resource.getInputStream());

                textureWidth = image.getWidth();
                textureHeight = image.getHeight();

                // 正确获取纹理ID
                mc.getTextureManager().bindTexture(textureLoc);
                flashTextureId = mc.getTextureManager().getTexture(textureLoc).getGlTextureId();
            } catch (IOException e) {
                e.printStackTrace();
                textureWidth = 1920;
                textureHeight = 1080;
                flashTextureId = -1;
            }
        }

        // 计算透明度的方法 - 改为public static以便访问
        public static float calculateFlashAlpha(int remainingTicks) {
            int elapsedTicks = FLASH_TOTAL_TICKS - remainingTicks;

            if (elapsedTicks < FADE_IN_TICKS) {
                return 0.3F * (elapsedTicks / (float) FADE_IN_TICKS);
            } else if (elapsedTicks < FADE_IN_TICKS + HOLD_TICKS) {
                return 0.3F;
            } else {
                int fadeOutElapsed = elapsedTicks - (FADE_IN_TICKS + HOLD_TICKS);
                return 0.3F * (1.0F - (fadeOutElapsed / (float) FADE_OUT_TICKS));
            }
        }

        // 备用白色覆盖层方法 - 改为public static以便访问
        public static void renderFallbackWhiteOverlay(Minecraft mc, int width, int height, float alpha) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            mc.ingameGUI.drawTexturedModalRect(0, 0, 0, 0, width, height);
        }
    }
}
