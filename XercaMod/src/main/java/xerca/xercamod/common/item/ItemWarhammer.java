package xerca.xercamod.common.item;

import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.*;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xerca.xercamod.common.Config;
import xerca.xercamod.common.XercaMod;
import xerca.xercamod.common.packets.HammerAttackPacket;
import xerca.xercamod.common.packets.HammerQuakePacket;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;

public class ItemWarhammer extends Item {
    private final float weaponDamage;
    private final float pushAmount;
    private final ItemTier material;

    public ItemWarhammer(String name, ItemTier mat) {
        super(new Item.Properties().group(ItemGroup.COMBAT).maxStackSize(1).defaultMaxDamage(mat.getMaxUses()));
        this.setRegistryName(name);
        this.material = mat;
        this.weaponDamage = 1.0F + mat.getAttackDamage();
        this.pushAmount = getPushFromMaterial(mat);

        this.addPropertyOverride(new ResourceLocation("pull"), new IItemPropertyGetter() {
            @OnlyIn(Dist.CLIENT)
            @Override
            public float call(@Nonnull ItemStack stack, World worldIn, LivingEntity entityIn) {
                if (entityIn == null) {
                    return 0.0F;
                } else {
                    ItemStack itemstack = entityIn.getActiveItemStack();
                    return ((itemstack.getItem() instanceof ItemWarhammer)) ? (float) (stack.getUseDuration() - entityIn.getItemInUseCount()) / (getFullUseSeconds(stack) * 20.0F) : 0.0F;
                }
            }
        });
        this.addPropertyOverride(new ResourceLocation("pulling"), new IItemPropertyGetter() {
            @OnlyIn(Dist.CLIENT)
            @Override
            public float call(@Nonnull ItemStack stack, World worldIn, LivingEntity entityIn) {
                return entityIn != null && entityIn.isHandActive() && entityIn.getActiveItemStack() == stack ? 1.0F : 0.0F;
            }
        });
    }

    private float getPushFromMaterial(ItemTier mat) {
        float push;
        if (mat == ItemTier.STONE) {
            push = 0.15f;
        } else if (mat == ItemTier.IRON) {
            push = 0.3f;
        } else if (mat == ItemTier.DIAMOND) {
            push = 0.4f;
        } else {
            push = 0.5f;
        }
        return push;
    }

    @Override
    public int getItemEnchantability()
    {
        return this.material.getEnchantability();
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment)
    {
        return enchantment.type == EnchantmentType.BREAKABLE || enchantment == Items.ENCHANTMENT_HEAVY ||
                enchantment == Items.ENCHANTMENT_QUAKE ||  enchantment == Items.ENCHANTMENT_MAIM ||
                enchantment == Items.ENCHANTMENT_QUICK ||enchantment == Items.ENCHANTMENT_UPPERCUT;
    }

    @Override
    public boolean hitEntity(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.damageItem(1, attacker, (p) -> p.sendBreakAnimation(Hand.MAIN_HAND));
        return true;
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World worldIn, BlockState blockIn, BlockPos pos, LivingEntity entityLiving) {
        if ((double) blockIn.getBlockHardness(worldIn, pos) != 0.0D) {
            stack.damageItem(1, entityLiving, (p) -> p.sendBreakAnimation(Hand.MAIN_HAND));
        }
        return true;
    }

    @Override
    public boolean canHarvestBlock(BlockState blockIn) {
        return blockIn.getBlock() == Blocks.STONE;
    }

    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        Ingredient ingr = this.material.getRepairMaterial();
        if (ingr.test(repair)) return true;
        return super.getIsRepairable(toRepair, repair);
    }

    @Nonnull
    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(@Nonnull EquipmentSlotType equipmentSlot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(equipmentSlot, stack);

        if (equipmentSlot == EquipmentSlotType.MAINHAND) {
            multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", (double) this.weaponDamage, AttributeModifier.Operation.ADDITION));
            multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", -3.0f, AttributeModifier.Operation.ADDITION));
        }

        return multimap;
    }

    @Nonnull
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    public float getFullUseSeconds(ItemStack stack){
        float seconds = 1.0f;
        if(stack.isEnchanted()){
            int heavyLevel = EnchantmentHelper.getEnchantmentLevel(Items.ENCHANTMENT_HEAVY, stack);
            if(heavyLevel > 0){
                float multiplier = 0.1f*(heavyLevel);
                seconds += seconds*multiplier;
            }
            else{
                int quickLevel = EnchantmentHelper.getEnchantmentLevel(Items.ENCHANTMENT_QUICK, stack);
                float multiplier = 0.12f*(quickLevel);
                seconds -= seconds*multiplier;
            }
        }
        return seconds;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, @Nonnull Hand hand) {
        final ItemStack heldItem = playerIn.getHeldItem(hand);
        playerIn.setActiveHand(hand);
        return new ActionResult<>(ActionResultType.SUCCESS, heldItem);
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World worldIn, LivingEntity entityLiving, int timeLeft) {
        if (!(entityLiving instanceof PlayerEntity)) return;

        PlayerEntity player = (PlayerEntity) entityLiving;

        // Number of seconds that the item has been used for
        float useSeconds = (this.getUseDuration(stack) - timeLeft) / 20.0f;
        float f = useSeconds / getFullUseSeconds(stack);
        if(f > 1.f) f = 1.f;

        if ((double) f >= 0.1D) {
            player.swingArm(Hand.MAIN_HAND);
            if (worldIn.isRemote) {
                Minecraft mine = Minecraft.getInstance();
                if (mine.objectMouseOver != null){
                    if(mine.objectMouseOver.getType() == RayTraceResult.Type.ENTITY) {
                        Entity target = ((EntityRayTraceResult) mine.objectMouseOver).getEntity();
                        HammerAttackPacket pack = new HammerAttackPacket(f, target.getEntityId());
                        XercaMod.NETWORK_HANDLER.sendToServer(pack);
                    }
                    else if(mine.objectMouseOver.getType() == RayTraceResult.Type.BLOCK){
                        if(EnchantmentHelper.getEnchantmentLevel(Items.ENCHANTMENT_QUAKE, stack) > 0){
                            HammerQuakePacket pack = new HammerQuakePacket(mine.objectMouseOver.getHitVec(), f);
                            XercaMod.NETWORK_HANDLER.sendToServer(pack);
                        }
                    }
                }

            }
        }
    }

    public float getPushAmount() {
        return pushAmount;
    }

    @Override
    @ParametersAreNonnullByDefault
    public void fillItemGroup(ItemGroup group, NonNullList<ItemStack> items) {
//        XercaMod.LOGGER.warn("WARHAMMER_ENABLE: " + Config.WARHAMMER_ENABLE.get());
        if(!Config.isWarhammerEnabled()){
            return;
        }
        super.fillItemGroup(group, items);
    }
}
