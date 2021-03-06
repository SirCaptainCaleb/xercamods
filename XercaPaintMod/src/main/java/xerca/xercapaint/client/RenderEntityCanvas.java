package xerca.xercapaint.client;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import xerca.xercapaint.common.XercaPaint;
import xerca.xercapaint.common.entity.EntityCanvas;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
public class RenderEntityCanvas extends EntityRenderer<EntityCanvas> {
    static private final ResourceLocation backLocation = new ResourceLocation("minecraft", "textures/block/birch_planks.png");

    private final TextureManager textureManager;
    private final Map<String, RenderEntityCanvas.Instance> loadedCanvases = Maps.newHashMap();

    RenderEntityCanvas(EntityRendererManager renderManager) {
        super(renderManager);
        this.textureManager = Minecraft.getInstance().textureManager;
    }

    @Nullable
    @Override
    public ResourceLocation getEntityTexture(EntityCanvas entity) {
        return getMapRendererInstance(entity).location;
    }

    @Override
    public void render(EntityCanvas entity, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn) {
        CompoundNBT tag = entity.getCanvasNBT();
        if(tag != null && tag.contains("name") ){
            getMapRendererInstance(entity).render(entity, entityYaw, matrixStackIn, bufferIn, entity.getHorizontalFacing());
        }
        super.render(entity, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
    }


    public static class RenderEntityCanvasFactory implements IRenderFactory<EntityCanvas> {
        @Override
        public EntityRenderer<? super EntityCanvas> createRenderFor(EntityRendererManager manager) {
            return new RenderEntityCanvas(manager);
        }
    }


//    public void updateMapTexture(CompoundNBT textureData) {
//        this.getMapRendererInstance(textureData).updateCanvasTexture(textureData);
//    }

    private RenderEntityCanvas.Instance getMapRendererInstance(EntityCanvas canvas) {
        CompoundNBT textureData = canvas.getCanvasNBT();
        RenderEntityCanvas.Instance instance = this.loadedCanvases.get(textureData.getString("name"));
        if (instance == null) {
            instance = new Instance(canvas);
            this.loadedCanvases.put(textureData.getString("name"), instance);
        }else{
            int currentVersion = textureData.getInt("v");
            if(instance.version < currentVersion){
                instance.updateCanvasTexture(textureData);
            }
        }

        return instance;
    }

    @Nullable
    public RenderEntityCanvas.Instance getMapInstanceIfExists(String name) {
        return this.loadedCanvases.get(name);
    }

    /**
     * Clears the currently loaded maps and removes their corresponding textures
     */
    public void clearLoadedMaps() {
        for(RenderEntityCanvas.Instance instance : this.loadedCanvases.values()) {
            instance.close();
        }

        this.loadedCanvases.clear();
    }

    public void close() {
        this.clearLoadedMaps();
    }

    @OnlyIn(Dist.CLIENT)
    class Instance implements AutoCloseable {
        int version = 0;
        int width;
        int height;
        private final DynamicTexture canvasTexture;
        private final ResourceLocation location;

        private Instance(EntityCanvas canvas) {
            CompoundNBT tag = canvas.getCanvasNBT();
            this.width = canvas.getWidthPixels();
            this.height = canvas.getHeightPixels();
            this.canvasTexture = new DynamicTexture(width, height, true);
            this.location = RenderEntityCanvas.this.textureManager.getDynamicTextureLocation("canvas/" + tag.getString("name"), this.canvasTexture);

            updateCanvasTexture(tag);
        }

        private int swapColor(int color){
            int i = (color & 16711680) >> 16;
            int j = (color & '\uff00') >> 8;
            int k = (color & 255);
            return k << 16 | j << 8 | i;
        }

        private void updateCanvasTexture(CompoundNBT textureData) {
            this.version = textureData.getInt("v");

            int[] pixels = textureData.getIntArray("pixels");

            if(pixels.length < height*width){
                XercaPaint.LOGGER.warn("Pixels array length (" + pixels.length + ") is smaller than canvas area (" + height*width + ")");
                return;
            }

            for (int i = 0; i < height; ++i) {
                for (int j = 0; j < width; ++j) {
                    int k = j + i * width;

                    canvasTexture.getTextureData().setPixelRGBA(j, i, swapColor(pixels[k]));
                }
            }

            canvasTexture.updateDynamicTexture();
        }

        public void render(EntityCanvas canvas, float yaw, MatrixStack ms, IRenderTypeBuffer buffer, Direction facing) {
            final float wScale = width/16.0f;
            final float hScale = height/16.0f;
            final double x = canvas.getPosX();
            final double y = canvas.getPosY();
            final double z = canvas.getPosZ();

            IVertexBuilder vb = buffer.getBuffer(RenderType.getEntitySolid(this.location));

            ms.push();
            final float xOffset = facing.getXOffset();
            final float zOffset = facing.getZOffset();
            final float yOffset = -1.0f;
            ms.translate(zOffset*0.5d*wScale, yOffset*0.5d*hScale, -xOffset*0.5d*wScale);
            ms.rotate(Vector3f.YP.rotationDegrees( 180-yaw));

            float f = 1.0f/32.0f;
            ms.scale(f, f, f);

            textureManager.bindTexture(location);

            int lightmap = WorldRenderer.getCombinedLight(canvas.world, new BlockPos(x, y, z));

            Matrix4f m = ms.getLast().getMatrix();
            Matrix3f mn = ms.getLast().getNormal();
            // Draw the front
            vb.pos(m, 0.0F, 32.0F*hScale, -1.0F).color(255, 255, 255, 255).tex(1.0F, 0.0F).overlay(OverlayTexture.NO_OVERLAY).lightmap(lightmap).normal(mn, xOffset, 0.0F, zOffset).endVertex();
            vb.pos(m,32.0F*wScale, 32.0F*hScale, -1.0F).color(255, 255, 255, 255).tex(0.0F, 0.0F).overlay(OverlayTexture.NO_OVERLAY).lightmap(lightmap).normal(mn, xOffset, 0.0F, zOffset).endVertex();
            vb.pos(m,32.0F*wScale, 0.0F, -1.0F).color(255, 255, 255, 255).tex(0.0F, 1.0F).overlay(OverlayTexture.NO_OVERLAY).lightmap(lightmap).normal(mn, xOffset, 0.0F, zOffset).endVertex();
            vb.pos(m,0.0F, 0.0F, -1.0F).color(255, 255, 255, 255).tex(1.0F, 1.0F).overlay(OverlayTexture.NO_OVERLAY).lightmap(lightmap).normal(mn, xOffset, 0.0F, zOffset).endVertex();

            vb = buffer.getBuffer(RenderType.getEntitySolid(backLocation));
            // Draw the back and sides
            final float sideWidth = 1.0F/16.0F;
            textureManager.bindTexture(backLocation);
            addVertex(vb, m, mn, 0.0D, 0.0D, 1.0D, 0.0F, 0.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, 1.0D, 1.0F, 0.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, 1.0D, 1.0F, 1.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, 1.0D, 0.0F, 1.0F, lightmap, xOffset, 0, zOffset);

            // Sides
            addVertex(vb, m, mn, 0.0D, 0.0D, 1.0D, sideWidth, 0.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, 1.0D, sideWidth, 1.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, -1.0D, 0.0F, 1.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 0.0D, 0.0D, -1.0D, 0.0F, 0.0F, lightmap, xOffset, 0, zOffset);

            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, 1.0F, 0.0F, 0.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, 1.0F, 1.0F, 0.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, -1.0F, 1.0F, sideWidth, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, -1.0F, 0.0F, sideWidth, lightmap, xOffset, 0, zOffset);

            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, -1.0F, 0.0F, 0.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, -1.0F, 0.0F, 1.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, 1.0F, sideWidth, 1.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, 1.0F, sideWidth, 0.0F, lightmap, xOffset, 0, zOffset);

            addVertex(vb, m, mn, 0.0D, 0.0D, -1.0F, 0.0F, 1.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, -1.0F, 1.0F, 1.0F, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, 1.0F, 1.0F, 1.0F-sideWidth, lightmap, xOffset, 0, zOffset);
            addVertex(vb, m, mn, 0.0D, 0.0D, 1.0F, 0.0F, 1.0F-sideWidth, lightmap, xOffset, 0, zOffset);

            ms.pop();
        }

        private void addVertex(IVertexBuilder vb, Matrix4f m, Matrix3f mn, double x, double y, double z, float tx, float ty, int lightmap, float xOff, float yOff, float zOff)
        {
            vb.pos(m, (float) x, (float)y, (float)z).color(255, 255, 255, 255).tex(tx, ty).overlay(OverlayTexture.NO_OVERLAY).lightmap(lightmap).normal(mn, xOff, yOff, zOff).endVertex();
        }

        public void close() {
            this.canvasTexture.close();
        }
    }
}