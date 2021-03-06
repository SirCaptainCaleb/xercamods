package xerca.xercamod.common.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;

class BlockApplePie extends CakeBlock {
    public BlockApplePie() {
        super(Block.Properties.create(Material.CAKE));
        this.setRegistryName("block_apple_pie");
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit){
        ActionResultType ate = super.onBlockActivated(state, worldIn, pos, player, handIn, hit);
        if(ate.isSuccess()){
            worldIn.playSound(null, player.getPosX(), player.getPosY(), player.getPosZ(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 1.0F, 1.0F + (worldIn.rand.nextFloat() - worldIn.rand.nextFloat()) * 0.4F);
        }
        return ate;
    }
}
