package de.guntram.mcmod.grid.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import de.guntram.mcmod.grid.Grid;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemGroup.DisplayContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    
    @Shadow @Final private BufferBuilderStorage bufferBuilders;

    @Redirect(
            method = "renderMain",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/FramePass;setRenderer(Ljava/lang/Runnable;)V")
    )
    private void wrapAndInject(FramePass instance, Runnable originalLambda, FrameGraphBuilder fgb, Frustum frustum, Camera camera, Matrix4f posMat, GpuBufferSlice fog, boolean outline, boolean entityOutline, RenderTickCounter tick) {
        instance.setRenderer(() -> {
            originalLambda.run(); // Run the original Minecraft code

            Vec3d pos = camera.getPos();
            var buffer = this.bufferBuilders.getEntityVertexConsumers().getBuffer(RenderLayer.getLines());
            Grid.instance.renderOverlay(tick.getDynamicDeltaTicks(), buffer, pos.x, pos.y, pos.z);
        });
    }
}
