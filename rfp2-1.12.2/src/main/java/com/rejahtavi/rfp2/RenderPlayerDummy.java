package com.rejahtavi.rfp2;

import com.rejahtavi.rfp2.compat.handlers.RFP2CompatHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/*
 * This class handles calls to draw the PlayerDummy object (except not really.)
 * 
 * When doRender() is called, we don't draw anything for the dummy itself.
 * Instead, we prepare some things, then call a vanilla player renderer at a very precise position relative to the camera.
 * 
 * In other words: The original doRender() call is *indirectly* triggering a vanilla player render operation,
 * instead of rendering our dummy entity (which is invisible anyway).
 * 
 * Using the vanilla renderer means we will automatically inherit any changes *other* mods have made to the player character,
 * but it also means that we have to deal with anything they add to the head that could block the view.
 */
public class RenderPlayerDummy extends Render<EntityPlayerDummy>
{
    // Constructor
    public RenderPlayerDummy(RenderManager renderManager)
    {
        // Call parent constructor
        super(renderManager);
    }
    
    // Handles requests for texture of the player dummy
    @Override
    protected ResourceLocation getEntityTexture(EntityPlayerDummy entity)
    {
        // The PlayerDummy entity is only for tracking the player's position, so it does not have a texture.
        return null;
    }
    
    // Implements linear interpolation for animation smoothing
    private float linearInterpolate(float current, float target, float partialTicks)
    {
        // Explanation for linear interpolation math can be found here:
        // https://en.wikipedia.org/wiki/Linear_interpolation
        return ((1 - partialTicks) * current) + (partialTicks * target);
    }
    
    // Called when the game wants to draw our PlayerDummy entity
    @Override
    public void doRender(EntityPlayerDummy renderEntity, double renderPosX, double renderPosY, double renderPosZ, float renderYaw, float partialTicks)
    {
        /*
         * NO-OP Checklist: We want to use as few CPU cycles as possible if we aren't
         * going to do anything useful. The following checks abort the render *as soon
         * as possible* if any of those conditions are true.
         */
        
        // Grab a reference to the local player entity, null-check it, and abort if it fails.
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return;
        
        // Grab a backup of any items we might possibly touch, so that we can be guaranteed
        // to be able to restore them when it comes time for the finally{} block to run.
        ItemStack itemMainHand   = player.inventory.getCurrentItem();
        ItemStack itemOffHand    = player.inventory.offHandInventory.get(0);
        ItemStack itemHelmetSlot = player.inventory.armorInventory.get(3);
        
        // Make quick per-frame compatibility checks based on current configuration and player state
        
        // Implement config option for disabling when sneaking
        if (RFP2Config.compatibility.disableWhenSneaking && player.isSneaking()) return;
        
        // Grab a reference to the vanilla player renderer, null check, and abort if it fails
        Render<AbstractClientPlayer> render         = (RenderPlayer) this.renderManager.<AbstractClientPlayer>getEntityRenderObject(player);
        RenderPlayer                 playerRenderer = (RenderPlayer) render;
        if (playerRenderer == null) return;
        
        // Grab a reference to the local player's model, null check, and abort if it fails
        ModelPlayer playerModel = (ModelPlayer) playerRenderer.getMainModel();
        if (playerModel == null) return;
        
        RFP2.logger.log(RFP2.LOGGING_LEVEL_HIGH, playerModel.getClass().getCanonicalName());
        
        // Grab a backup of the various player model layers we might adjust
        // This way we aren't making any assumptions about what other mods might be doing with these options
        // and we can restore everything when we are finished.
        boolean[] modelState = { playerModel.bipedHead.isHidden,
                                 playerModel.bipedHead.showModel,
                                 playerModel.bipedHeadwear.isHidden,
                                 playerModel.bipedHeadwear.showModel,
                                 playerModel.bipedLeftArm.isHidden,
                                 playerModel.bipedLeftArm.showModel,
                                 playerModel.bipedLeftArmwear.isHidden,
                                 playerModel.bipedLeftArmwear.showModel,
                                 playerModel.bipedRightArm.isHidden,
                                 playerModel.bipedRightArm.showModel,
                                 playerModel.bipedRightArmwear.isHidden,
                                 playerModel.bipedRightArmwear.showModel
        };
        
        /*
         * With the routine, unlikely-to-fail stuff out of the way, try to make the remainder
         * of this routine as fail-safe as possible. It runs every frame, so we want to be able
         * to stop it from running anymore if we encounter a problem, to avoid slowing down the
         * game and consuming disk space with useless error logs.
         */
        try
        {
            // Note: thirdPersonView can be: 0 = First Person, 1 = Third Person Rear, 2 = Third Person
            // If the player is NOT in first person, do nothing
            if (Minecraft.getMinecraft().gameSettings.thirdPersonView != 0) return;
            
            // If the player is flying with an Elytra, do nothing
            if (player.isElytraFlying()) return;
            
            // (Keep this NO-OP check last, it can be more expensive than the others, due to the mount check.)
            // If mod is not enabled this frame, do nothing
            if (!RFP2.state.isModEnabled(player)) return;
            
            // Check if any of the compatibility handlers want us to skip this frame 
            for (RFP2CompatHandler handler : RFP2.compatHandlers)
            {
                if (handler.getDisableRFP2(player))
                {
                    return;
                }
            }
            
            /*
             * Initialization: Pull in state info and set up local variables, now that we
             * know we actually need to do some rendering.
             */
            
            // Initialize remaining local variables
            double playerRenderPosX  = 0;
            double playerRenderPosZ  = 0;
            double playerRenderPosY  = 0;
            float  playerRenderAngle = 0;
            
            // Get local copies of config & state
            float   playerModelOffset     = (float) RFP2Config.preferences.playerModelOffset;
            boolean isRealArmsEnabled     = RFP2.state.isRealArmsEnabled(player);
            boolean isHeadRotationEnabled = RFP2.state.isHeadRotationEnabled(player);
            
            /*
             * Adjust Player Model:
             * Strip unwanted items and layers from the player model to avoid obstructing the camera
             */
            
            // Remove the player's helmet, so that it does not obstruct the camera.
            player.inventory.armorInventory.set(3, ItemStack.EMPTY);
            
            // Hide the player model's head layers, again so they do not obstruct the camera.
            playerModel.bipedHead.isHidden      = true;
            playerModel.bipedHead.showModel     = false;
            playerModel.bipedHeadwear.isHidden  = true;
            playerModel.bipedHeadwear.showModel = false;
            
            // Instruct compatibility handlers hide head models (handlers are responsible for caching state for later restoration) 
            for (RFP2CompatHandler handler : RFP2.compatHandlers)
            {
                handler.hideHead(player, true);
            }
            
            // Check if we need to hide the arms
            if (!isRealArmsEnabled)
            {
                // The real arms feature is not enabled, so we should NOT render the arms in 3D.
                // That means we need to hide them before we draw the player model.
                // Remove the player's currently held main and off hand items, so that they do not obstruct the camera.
                player.inventory.removeStackFromSlot(player.inventory.currentItem);
                player.inventory.offHandInventory.set(0, ItemStack.EMPTY);
                
                // Hide the player model's arm layers
                playerModel.bipedLeftArm.isHidden       = true;
                playerModel.bipedLeftArm.showModel      = false;
                playerModel.bipedRightArm.isHidden      = true;
                playerModel.bipedRightArm.showModel     = false;
                playerModel.bipedLeftArmwear.isHidden   = true;
                playerModel.bipedLeftArmwear.showModel  = false;
                playerModel.bipedRightArmwear.isHidden  = true;
                playerModel.bipedRightArmwear.showModel = false;
                
                // Instruct compatibility handlers hide arm models (handlers are responsible for caching state for later restoration) 
                for (RFP2CompatHandler handler : RFP2.compatHandlers)
                {
                    handler.hideArms(player, true);
                }
            }
            
            /*
             * Calculate Rendering Coordinates:
             * Determine the precise location and angle the player should be rendered this frame, then render it.
             * 
             * Notes:
             *      player.rotationYaw     = The direction the player's camera is facing.
             *      player.renderYawOffset = The direction the player's 3D model is facing.
             * 
             * (In vanilla minecraft, the player's body lags behind the head to provide a more natural look to movement.
             * This can lead to renderYawOffset being off from the main camera by up to 75 degrees!)
             */
            
            // Generate default rendering coordinates for player body
            playerRenderPosX = player.posX - renderEntity.posX + renderPosX;
            playerRenderPosY = player.posY - renderEntity.posY + renderPosY;
            playerRenderPosZ = player.posZ - renderEntity.posZ + renderPosZ;
            
            // If the player IS sleeping, we can skip any extra calculations and proceed directly to rendering.
            if (!player.isPlayerSleeping())
            {
                // The player is NOT sleeping, so we are going to need to make some adjustments.
                // If head rotation IS enabled, then we DO NOT need to counteract it and can skip the next adjustment.
                if (!isHeadRotationEnabled)
                {
                    // Head rotation is NOT enabled, so we have to prevent the vanilla rotation behavior!
                    // We can do this by updating the player's body's rendering values to match the camera's
                    // position before rendering each frame. It is *critical* that we update the data for both
                    // this frame and the previous one, or else our linear interpolation will be fed bad
                    // data and the result will not look smooth!
                    player.renderYawOffset     = player.rotationYaw;
                    player.prevRenderYawOffset = player.prevRotationYaw;
                }
                
                // Interpolate to get final rendering position
                playerRenderAngle = this.linearInterpolate(player.prevRenderYawOffset, player.renderYawOffset, partialTicks);
                
                // Update position of rendered body to include interpolation and model offset
                playerRenderPosX += (playerModelOffset * Math.sin(Math.toRadians(playerRenderAngle)));
                playerRenderPosZ -= (playerModelOffset * Math.cos(Math.toRadians(playerRenderAngle)));
            }
            
            /*
             * Trigger rendering of the fake player model: this is done by calling the
             * vanilla player renderer with our adjusted coordinates.
             * 
             * Note that we do NOT pass the playerModel here -- the vanilla renderer already
             * has it! That's why we had to actually remove the player's helmet and possibly
             * other inventory items. That also means that it is *critical* that we undo all
             * of those changes immediately after this, before anything outside of RFP2 can
             * interact with these objects and invalidate our cached state.
             * 
             * (That is also why those reversions are implemented within a finally block.)
             */
            playerRenderer.doRender(player, playerRenderPosX, playerRenderPosY, playerRenderPosZ, playerRenderAngle, partialTicks);
        }
        catch (Exception e)
        {
            // If anything goes wrong, shut the mod off and write an error to the logs.
            RFP2.errorDisableMod(this.getClass().getName() + ".doRender()", e);
        }
        finally
        {
            /*false
             * Cleanup Phase:
             * Revert all temporary changes we made to the model for rendering purposes, so that we don't cause any side effects.
             *
             * Whether or not something went wrong, we want to make ABSOLUTELY SURE to give
             * back any items we took and re-enable all player model rendering layers.
             * 
             * If we fail to do this, we could glitch out the player's rendering, or worse,
             * accidentally delete someone's hard won inventory items!
             */
            
            // restore the player's inventory to the state we found it in
            player.inventory.armorInventory.set(3, itemHelmetSlot);
            player.inventory.setInventorySlotContents(player.inventory.currentItem, itemMainHand);
            player.inventory.offHandInventory.set(0, itemOffHand);
            
            // restore the player model's rendering layers
            playerModel.bipedHead.isHidden          = modelState[0];
            playerModel.bipedHead.showModel         = modelState[1];
            playerModel.bipedHeadwear.isHidden      = modelState[2];
            playerModel.bipedHeadwear.showModel     = modelState[3];
            playerModel.bipedLeftArm.isHidden       = modelState[4];
            playerModel.bipedLeftArm.showModel      = modelState[5];
            playerModel.bipedLeftArmwear.isHidden   = modelState[6];
            playerModel.bipedLeftArmwear.showModel  = modelState[7];
            playerModel.bipedRightArm.isHidden      = modelState[8];
            playerModel.bipedRightArm.showModel     = modelState[9];
            playerModel.bipedRightArmwear.isHidden  = modelState[10];
            playerModel.bipedRightArmwear.showModel = modelState[11];
            
            // Instruct compatibility handlers restore head models 
            for (RFP2CompatHandler handler : RFP2.compatHandlers)
            {
                handler.restoreHead(player, true);
                handler.restoreArms(player, true);
            }
        }
    }
}
