package de.guntram.mcmod.grid.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import de.guntram.mcmod.grid.Grid;
import net.minecraft.client.render.*;
import net.minecraft.client.render.state.WorldRenderState;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    
    @Shadow @Final private BufferBuilderStorage bufferBuilders;

    @Inject(
            method = "method_62214",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",
                    args = "ldc=destroyProgress"
            ),
            remap = false // Critical: synthetic methods aren't in the mapping files
    )
    public void renderGrid(GpuBufferSlice gpuBufferSlice, WorldRenderState worldRenderState, Profiler profiler, Matrix4f matrix4f, Handle handle, Handle handle2, boolean bl, Handle handle3, Handle handle4, CallbackInfo ci) {
              Vec3d vec3d = camera.getPos();
        double x = vec3d.getX();
        double y = vec3d.getY();
        double z = vec3d.getZ();
        VertexConsumerProvider.Immediate immediate = this.bufferBuilders.getEntityVertexConsumers();
        Grid.instance.renderOverlay(renderTickCounter.getDynamicDeltaTicks(), immediate.getBuffer(RenderLayer.getLines()), x, y, z);
    }
}
