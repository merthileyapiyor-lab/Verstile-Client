package com.verstile.client.module.movement;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import com.verstile.client.setting.BooleanSetting;
import com.verstile.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * FreeCam — kamerayı karakterden bağımsız olarak serbestçe gezdirme.
 * Aktifken karakter olduğu yerde durur, kamera WASD + space/shift ile
 * hareket eder. Devre dışıyken karakter eski konumuna geri döner.
 */
public class FreeCam extends Module {
    public final NumberSetting speed    = new NumberSetting("Camera Speed", 0.5, 0.05, 3.0, 0.05);
    public final BooleanSetting freeze  = new BooleanSetting("Freeze Player", true);
    public final BooleanSetting noClip  = new BooleanSetting("No Clip", true);

    private double anchorX, anchorY, anchorZ;
    private Entity previousCamera;
    private ArmorStand freeCamera;
    private Object activeLevel;

    public FreeCam() {
        super("FreeCam", "Kamerayı karakterden bağımsız olarak serbestçe gezdirme", Category.MOVEMENT, 0);
        addSetting(speed);
        addSetting(freeze);
        addSetting(noClip);
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        anchorX = mc.player.getX();
        anchorY = mc.player.getY();
        anchorZ = mc.player.getZ();
        activeLevel = mc.level;
        previousCamera = mc.getCameraEntity();

        freeCamera = new ArmorStand(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        // Match the player's current eye position even though an armor stand has
        // a different eye height. The entity is never added to the world/server.
        freeCamera.setPos(mc.player.getX(), mc.player.getEyeY() - freeCamera.getEyeHeight(), mc.player.getZ());
        freeCamera.setYRot(mc.player.getYRot());
        freeCamera.setXRot(mc.player.getXRot());
        freeCamera.setYHeadRot(mc.player.getYRot());
        freeCamera.setInvisible(true);
        freeCamera.setNoGravity(true);
        freeCamera.noPhysics = noClip.getValue();
        mc.setCameraEntity(freeCamera);
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        Entity restore = previousCamera;
        if (restore == null || restore.level() != mc.level) restore = mc.player;
        if (restore != null) mc.setCameraEntity(restore);
        if (mc.player != null && freeze.getValue()) mc.player.setDeltaMovement(Vec3.ZERO);
        previousCamera = null;
        freeCamera = null;
        activeLevel = null;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || freeCamera == null || activeLevel != mc.level) {
            setEnabled(false);
            return;
        }

        double spd = speed.getValue();
        // Minecraft applies mouse input to the local player even while another
        // entity is used as the camera. The render-frame sync below mirrors that
        // rotation to the detached camera without limiting mouse look to 20 FPS.
        float yaw   = mc.player.getYRot();
        double yawRad   = Math.toRadians(yaw);
        double sinYaw   = Math.sin(yawRad);
        double cosYaw   = Math.cos(yawRad);

        double dx = 0, dy = 0, dz = 0;
        if (mc.options.keyUp.isDown()) {
            dx -= sinYaw;
            dz += cosYaw;
        }
        if (mc.options.keyDown.isDown()) {
            dx += sinYaw;
            dz -= cosYaw;
        }
        if (mc.options.keyLeft.isDown()) {
            dx += cosYaw;
            dz += sinYaw;
        }
        if (mc.options.keyRight.isDown()) {
            dx -= cosYaw;
            dz -= sinYaw;
        }
        if (mc.options.keyJump.isDown()) dy += 1.0;
        if (mc.options.keyShift.isDown()) dy -= 1.0;

        Vec3 movement = new Vec3(dx, dy, dz);
        if (movement.lengthSqr() > 1.0) movement = movement.normalize();
        movement = movement.scale(spd);

        freeCamera.noPhysics = noClip.getValue();

        // Preserve a valid previous transform so Minecraft's partial-tick camera
        // interpolation stays smooth even though this entity is not in the world.
        freeCamera.setOldPosAndRot();

        if (noClip.getValue()) {
            freeCamera.setPos(freeCamera.position().add(movement));
        } else {
            freeCamera.move(MoverType.SELF, movement);
        }

        if (freeze.getValue()) {
            // Avoid constantly rewriting the player's transform: that caused
            // visible correction jitter and unnecessary movement packets.
            if (mc.player.distanceToSqr(anchorX, anchorY, anchorZ) > 0.0025) {
                mc.player.setPos(anchorX, anchorY, anchorZ);
            }
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public void onRender() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || freeCamera == null || activeLevel != mc.level) return;

        // onRender runs once per frame, so mouse look remains as smooth as the
        // display refresh rate instead of visibly stepping at the 20 TPS rate.
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        freeCamera.setYRot(yaw);
        freeCamera.setYHeadRot(yaw);
        freeCamera.setXRot(pitch);
    }
}
