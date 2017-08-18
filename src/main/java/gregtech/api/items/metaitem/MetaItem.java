package gregtech.api.items.metaitem;

import com.google.common.collect.ImmutableList;
import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TShortObjectHashMap;
import gregtech.api.GregTech_API;
import gregtech.api.GT_Values;
import gregtech.api.items.OreDictNames;
import gregtech.api.items.metaitem.stats.*;
import gregtech.api.items.GenericItem;
import gregtech.api.unification.OreDictionaryUnifier;
import gregtech.api.unification.material.type.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.ItemMaterialInfo;
import ic2.api.item.IBoxable;
import ic2.api.item.ISpecialElectricItem;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.capability.wrappers.FluidContainerItemWrapper;
import net.minecraftforge.fml.common.IFuelHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * MetaItem is item that can have up to Short.MAX_VALUE items inside one id.
 * These items even can be edible, have custom behaviours, be electric or act like fluid containers!
 * They can also have different burn time, plus be handheld, oredicted, no-unificated or invisible!
 * They also can be reactor components.
 *
 * You can also extend this class and occupy some of it's MetaData, and just pass an meta offset in constructor, and everything will work properly.
 *
 * Items are added in MetaItem via {@link #addItem(int, String, String...)}. You will get {@link MetaValueItem} instance, which you can configure in builder-alike pattern:
 * {@code addItem(0).setHandheld().addStats(new ElectricStats(10000, 1,  false)) }
 * This will add single-use handheld-rendered (unchargeable) LV battery with initial capacity 10000 EU
 */
@SuppressWarnings("unchecked")
public class MetaItem<T extends MetaItem.MetaValueItem> extends GenericItem implements ISpecialElectricItem, IFluidContainerItem, IFuelHandler, IReactorComponent, IBoxable {

    private TShortObjectMap<T> metaItems = new TShortObjectHashMap<>();

    protected final short metaItemOffset;

    public MetaItem(String unlocalizedName, short metaItemOffset) {
        super(unlocalizedName);
        setHasSubtypes(true);
        setCreativeTab(GregTech_API.TAB_GREGTECH_MATERIALS);
        this.metaItemOffset = metaItemOffset;
    }

    protected T constructMetaValueItem(short metaValue, String unlocalizedName, String... nameParameters) {
        return (T) new MetaValueItem(metaValue, unlocalizedName, nameParameters);
    }

    public final T addItem(int metaValue, String unlocalizedName, String... nameParameters) {
        Validate.inclusiveBetween(0, Short.MAX_VALUE - 1, metaValue, "MetaItem ID should be in range from 0 to Short.MAX_VALUE-1");
        T metaValueItem = constructMetaValueItem((short) metaValue, unlocalizedName, nameParameters);
        metaItems.put((short) metaValue, metaValueItem);
        return metaValueItem;
    }

    public final T getItem(short metaValue) {
        return metaItems.get(metaValue);
    }

    public final T getItem(ItemStack itemStack) {
        return getItem((short) (itemStack.getItemDamage() - metaItemOffset));
    }

    //////////////////////////////////////////////////////////////////
    //                 ISpecialElectricItem Implementation          //
    //////////////////////////////////////////////////////////////////

    private IElectricStats getElectricStats(ItemStack itemStack) {
        MetaValueItem metaValueItem = getItem(itemStack);
        if(metaValueItem == null) {
            return ElectricStats.EMPTY;
        }
        return metaValueItem.getElectricStats();
    }

    @Override
    public IElectricStats getManager(ItemStack itemStack) {
        return getElectricStats(itemStack);
    }

    //////////////////////////////////////////////////////////////////
    //                 IFluidContainer Implementation               //
    //////////////////////////////////////////////////////////////////

    private IFluidStats getFluidStats(ItemStack itemStack) {
        MetaValueItem metaValueItem = getItem(itemStack);
        if(metaValueItem == null) {
            return FluidStats.EMPTY;
        }
        return metaValueItem.getFluidStats();
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new FluidContainerItemWrapper(this, stack);
    }

    @Override
    public FluidStack getFluid(ItemStack container) {
        return getFluidStats(container).getFluid(container);
    }

    @Override
    public int getCapacity(ItemStack container) {
        return getFluidStats(container).getCapacity(container);
    }

    @Override
    public int fill(ItemStack container, FluidStack resource, boolean doFill) {
        return getFluidStats(container).fill(container, resource, doFill);
    }

    @Override
    public FluidStack drain(ItemStack container, int maxDrain, boolean doDrain) {
        return getFluidStats(container).drain(container, maxDrain, doDrain);
    }

    //////////////////////////////////////////////////////////////////
    //                 INuclearStats  Implementation            //
    //////////////////////////////////////////////////////////////////
    
    private INuclearStats getNuclearStats(ItemStack itemStack) {
        MetaValueItem metaValueItem = getItem(itemStack);
        if(metaValueItem == null) {
            return null;
        }
        return metaValueItem.getNuclearStats();
    }

    @Override
    public boolean canBeStoredInToolbox(ItemStack stack) {
        MetaValueItem metaValueItem = getItem(stack);
        INuclearStats nuclearStats = metaValueItem.getNuclearStats();
        return metaValueItem != null && nuclearStats != null;
    }

    @Override
    public void processChamber(ItemStack stack, IReactor reactor, int x, int y, boolean heatrun) {
        INuclearStats nuclearStats = getNuclearStats(stack);
        if(nuclearStats != null) {
            nuclearStats.processChamber(stack, reactor, x, y, heatrun);
        }
    }

    @Override
    public boolean acceptUraniumPulse(ItemStack stack, IReactor reactor, ItemStack pulsingStack, int youX, int youY, int pulseX, int pulseY, boolean heatrun) {
        INuclearStats nuclearStats = getNuclearStats(stack);
        if(nuclearStats != null) {
            return nuclearStats.acceptUraniumPulse(stack, reactor, pulsingStack, youX, youY, pulseX, pulseY, heatrun);
        }
        return false;
    }

    @Override
    public boolean canStoreHeat(ItemStack stack, IReactor reactor, int x, int y) {
        INuclearStats nuclearStats = getNuclearStats(stack);
        if(nuclearStats != null) {
            return nuclearStats.canStoreHeat(stack, reactor, x, y);
        }
        return false;
    }

    @Override
    public int getMaxHeat(ItemStack stack, IReactor reactor, int x, int y) {
        INuclearStats nuclearStats = getNuclearStats(stack);
        if(nuclearStats != null) {
            return nuclearStats.getMaxHeat(stack, reactor, x, y);
        }
        return 0;
    }

    @Override
    public int getCurrentHeat(ItemStack stack, IReactor reactor, int x, int y) {
        INuclearStats nuclearStats = getNuclearStats(stack);
        if(nuclearStats != null) {
            return nuclearStats.getCurrentHeat(stack, reactor, x, y);
        }
        return 0;
    }

    @Override
    public int alterHeat(ItemStack stack, IReactor reactor, int x, int y, int heat) {
        INuclearStats nuclearStats = getNuclearStats(stack);
        if(nuclearStats != null) {
            return nuclearStats.alterHeat(stack, reactor, x, y, heat);
        }
        return heat;
    }

    @Override
    public float influenceExplosion(ItemStack stack, IReactor reactor) {
        INuclearStats nuclearStats = getNuclearStats(stack);
        if(nuclearStats != null) {
            return nuclearStats.influenceExplosion(stack, reactor);
        }
        return 1.0f;
    }

    @Override
    public boolean canBePlacedIn(ItemStack stack, IReactor reactor) {
        INuclearStats nuclearStats = getNuclearStats(stack);
        return nuclearStats != null && nuclearStats.canBePlacedIn(stack, reactor);
    }

    //////////////////////////////////////////////////////////////////
    //                 IFuelHandler   Implementation                //
    //////////////////////////////////////////////////////////////////

    private int getBurnValue(ItemStack itemStack) {
        MetaValueItem metaValueItem = getItem(itemStack);
        if(metaValueItem == null) {
            return 0;
        }
        return metaValueItem.getBurnValue();
    }

    @Override
    public int getBurnTime(ItemStack fuel) {
        return getBurnValue(fuel);
    }

    //////////////////////////////////////////////////////////////////
    //      Behaviours and Use Manager Implementation               //
    //////////////////////////////////////////////////////////////////

    private IItemUseManager getUseManager(ItemStack itemStack) {
        MetaValueItem metaValueItem = getItem(itemStack);
        if(metaValueItem == null) {
            return null;
        }
        return metaValueItem.getUseManager();
    }

    private IItemDurabilityManager getDurabilityManager(ItemStack itemStack) {
        MetaValueItem metaValueItem = getItem(itemStack);
        if(metaValueItem == null) {
            return null;
        }
        return metaValueItem.getDurabilityManager();
    }

    public List<IItemBehaviour> getBehaviours(ItemStack itemStack) {
        MetaValueItem metaValueItem = getItem(itemStack);
        if(metaValueItem == null) {
            return ImmutableList.of();
        }
        return metaValueItem.getBehaviours();
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        MetaValueItem metaValueItem = getItem(stack);
        if(metaValueItem == null) {
            return 64;
        }
        return metaValueItem.getMaxStackSize();
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        IItemUseManager useManager = getUseManager(stack);
        if(useManager != null) {
            return useManager.getUseAction(stack);
        }
        return EnumAction.NONE;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        IItemUseManager useManager = getUseManager(stack);
        if(useManager != null) {
            return useManager.getMaxItemUseDuration(stack);
        }
        return 0;
    }


    @Override
    public void onUsingTick(ItemStack stack, EntityLivingBase player, int count) {
        if(player instanceof EntityPlayer) {
            IItemUseManager useManager = getUseManager(stack);
            if(useManager != null) {
                useManager.onItemUsingTick(stack, (EntityPlayer) player, count);
            }
        }
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityLivingBase player, int timeLeft) {
        if(player instanceof EntityPlayer) {
            IItemUseManager useManager = getUseManager(stack);
            if(useManager != null) {
                useManager.onPlayerStoppedItemUsing(stack, (EntityPlayer) player, timeLeft);
            }
        }
    }

    @Nullable
    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase player) {
        if(player instanceof EntityPlayer) {
            IItemUseManager useManager = getUseManager(stack);
            if(useManager != null) {
                return useManager.onItemUseFinish(stack, (EntityPlayer) player);
            }
        }
        return stack;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        boolean returnValue = false;
        for(IItemBehaviour behaviour : getBehaviours(stack)) {
            if(behaviour.onLeftClickEntity(stack, player, entity)) {
                returnValue = true;
            }
        }
        return returnValue;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(ItemStack itemStack, World world, EntityPlayer player, EnumHand hand) {
        for(IItemBehaviour behaviour : getBehaviours(itemStack)) {
            ActionResult<ItemStack> behaviourResult = behaviour.onItemRightClick(itemStack, world, player, hand);
            itemStack = behaviourResult.getResult();
            if (itemStack.stackSize == 0) itemStack = null;
            if (behaviourResult.getType() != EnumActionResult.PASS) {
                return ActionResult.newResult(behaviourResult.getType(), itemStack);
            } else if (itemStack == null) {
                return ActionResult.newResult(EnumActionResult.PASS, null);
            }
        }
        IItemUseManager useManager = getUseManager(itemStack);
        if(useManager != null && useManager.canStartUsing(itemStack, player)) {
            useManager.onItemUseStart(itemStack, player);
            player.setActiveHand(hand);
            return ActionResult.newResult(EnumActionResult.SUCCESS, itemStack);
        }
        return ActionResult.newResult(EnumActionResult.PASS, itemStack);
    }

    @Override
    public EnumActionResult onItemUse(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack originalStack = stack.copy();
        for(IItemBehaviour behaviour : getBehaviours(stack)) {
            ActionResult<ItemStack> behaviourResult = behaviour.onItemUse(stack, player, world, pos, hand, facing, hitX, hitY, hitZ);
            stack = behaviourResult.getResult();
            if(stack.stackSize == 0) stack = null;
            if(behaviourResult.getType() != EnumActionResult.PASS) {
                if(!ItemStack.areItemStacksEqual(originalStack, stack))
                    player.setHeldItem(hand, stack);
                return behaviourResult.getType();
            } else if(stack == null) {
                player.setHeldItem(hand, null);
                return EnumActionResult.PASS;
            }
        }
        EnumAction useAction = getItemUseAction(stack);
        if(useAction != EnumAction.NONE) {
            player.setActiveHand(hand);
            return EnumActionResult.SUCCESS;
        }
        if(!ItemStack.areItemStacksEqual(originalStack, stack))
            player.setHeldItem(hand, stack);
        return EnumActionResult.PASS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public String getItemStackDisplayName(ItemStack stack) {
        if (stack.getItemDamage() >= metaItemOffset) {
            T item = getItem(stack);

            if (item.nameParameters.length != 0) {
                String[] localizedParams = new String[item.nameParameters.length];
                for (int i = 0; i < item.nameParameters.length; i++) {
                    localizedParams[i] = I18n.format(item.nameParameters[i]);
                }

                return I18n.format("metaitem." + item.unlocalizedName + ".name", (Object[]) localizedParams);
            }
            return I18n.format("metaitem." + item.unlocalizedName + ".name");
        }
        return super.getItemStackDisplayName(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemStack, EntityPlayer player, List<String> lines, boolean showAdditionalInfo) {
        super.addInformation(itemStack, player, lines, showAdditionalInfo);

        String unlocalizedTooltip = "metaitem." + getItem(itemStack).unlocalizedName + ".tooltip";
        if (I18n.hasKey(unlocalizedTooltip)) {
            lines.add(I18n.format(unlocalizedTooltip));
        }

        IElectricStats electricStats = getManager(itemStack);
        if(electricStats.getMaxCharge(itemStack) > 0) {
            lines.add(I18n.format("item.gt.meta_item.electric_info",
                    electricStats.getCharge(itemStack),
                    electricStats.getMaxCharge(itemStack),
                    GT_Values.V[electricStats.getTier(itemStack)]));
        }
        if(getCapacity(itemStack) > 0) {
            FluidStack fluid = getFluid(itemStack);
            if(fluid != null) {
                lines.add(I18n.format("item.gt.meta_item.fluid_info",
                        fluid.amount,
                        getCapacity(itemStack),
                        fluid.getLocalizedName()));
            } else lines.add(I18n.format("item.gt.meta_item.fluid_info_empty"));
        }
        for(IItemBehaviour behaviour : getBehaviours(itemStack)) {
            behaviour.addInformation(itemStack, lines);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item itemIn, CreativeTabs tab, List<ItemStack> subItems) {
        for(MetaValueItem enabledItem : metaItems.valueCollection()) {
            if(enabledItem.isVisible()) {
                ItemStack itemStack = enabledItem.getStackForm();
                IElectricStats electricStats = getManager(itemStack);
                if(getCapacity(itemStack) > 0) {
                    for(Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
                        if(electricStats.getMaxCharge(itemStack) > 0) {
                            ItemStack chargedFilledStack = itemStack.copy();
                            fill(chargedFilledStack, new FluidStack(fluid, Integer.MAX_VALUE), true);
                            electricStats.charge(chargedFilledStack, Integer.MAX_VALUE, Integer.MAX_VALUE, true, false);
                            subItems.add(chargedFilledStack);
                        } else {
                            ItemStack filledStack = itemStack.copy();
                            fill(filledStack, new FluidStack(fluid, Integer.MAX_VALUE), true);
                            subItems.add(filledStack);
                        }
                    }
                }
                if(electricStats.getMaxCharge(itemStack) > 0) {
                    ItemStack chargedStack = itemStack.copy();
                    electricStats.charge(chargedStack, Integer.MAX_VALUE, Integer.MAX_VALUE, true, false);
                    subItems.add(chargedStack);
                }
                subItems.add(itemStack);
            }
        }
    }

    public class MetaValueItem {

        public MetaItem<T> getMetaItem() {
            return MetaItem.this;
        }

        public final int metaValue;

        public final String unlocalizedName;

        //Parameters can be either localized or not
        protected final String[] nameParameters;

        private IElectricStats electricStats;
        private IFluidStats fluidStats;
        private INuclearStats nuclearStats;
        private List<IItemBehaviour> behaviours = new ArrayList<>();
        private IItemUseManager useManager;
        private IItemDurabilityManager durabilityManager;
        private int burnValue = 0;
        private boolean visible = true;
        private int maxStackSize = 64;

        protected MetaValueItem(int metaValue, String unlocalizedName, String... nameParameters) {
            this.metaValue = metaValue;
            this.unlocalizedName = unlocalizedName;
            this.nameParameters = nameParameters;
        }

        public MetaValueItem setMaterialInfo(ItemMaterialInfo materialInfo) {
            if(materialInfo == null) {
                throw new IllegalArgumentException("Cannot add null ItemMaterialInfo.");
            }
            OreDictionaryUnifier.registerOre(getStackForm(), materialInfo);
            return this;
        }

        public MetaValueItem setUnificationData(OrePrefix prefix, @Nullable Material material) {
            if(prefix == null) {
                throw new IllegalArgumentException("Cannot add null OrePrefix.");
            }

            OreDictionaryUnifier.registerOre(getStackForm(), prefix, material);
            return this;
        }

        public MetaValueItem addOreDict(String oreDictName) {
            if(oreDictName == null) {
                throw new IllegalArgumentException("Cannot add null OreDictName.");
            }
            OreDictionary.registerOre(oreDictName, getStackForm());
            return this;
        }

        public MetaValueItem addOreDict(OreDictNames oreDictName) {
            if(oreDictName == null) {
                throw new IllegalArgumentException("Cannot add null OreDictName.");
            }
            OreDictionary.registerOre(oreDictName.name(), getStackForm());
            return this;
        }

        public MetaValueItem setInvisible() {
            this.visible = false;
            return this;
        }

        public MetaValueItem setMaxStackSize(int maxStackSize) {
            if(maxStackSize <= 0) {
                throw new IllegalArgumentException("Cannot set Max Stack Size to negative or zero value.");
            }
            this.maxStackSize = maxStackSize;
            return this;
        }

        public MetaValueItem setBurnValue(int burnValue) {
            if(burnValue <= 0) {
                throw new IllegalArgumentException("Cannot set Burn Value to negative or zero number.");
            }
            this.burnValue = burnValue;
            return this;
        }

        public MetaValueItem addStats(IMetaItemStats... stats) {
            for(IMetaItemStats metaItemStats : stats) {
                if(metaItemStats instanceof IItemDurabilityManager) {
                    setDurabilityManager((IItemDurabilityManager) metaItemStats);
                }
                if(metaItemStats instanceof IItemUseManager) {
                    setUseManager((IItemUseManager) metaItemStats);
                }
                if(metaItemStats instanceof IFoodStats) {
                    setFoodStats((IFoodStats) metaItemStats);
                }
                if (metaItemStats instanceof IItemBehaviour) {
                    addBehaviour((IItemBehaviour) metaItemStats);
                }
            }
            return this;
        }

        protected void setFoodStats(IFoodStats foodStats) {
            addBehaviour(new FoodUseManager(foodStats));
        }

        protected void setDurabilityManager(IItemDurabilityManager durabilityManager) {
            if(durabilityManager == null) {
                throw new IllegalArgumentException("Cannot set Durability Manager to null.");
            }
            if(this.durabilityManager != null) {
                throw new IllegalStateException("Tried to set Durability Manager to " + durabilityManager + ", but they're already set to " + this.durabilityManager);
            }
            this.durabilityManager = durabilityManager;
        }

        protected void setElectricStats(IElectricStats electricStats) {
            if(electricStats == null) {
                throw new IllegalArgumentException("Cannot set Electric Stats to null.");
            }
            if(this.electricStats != null) {
                throw new IllegalStateException("Tried to set Electric Stats to " + electricStats + ", but they're already set to " + this.electricStats);
            }
            this.electricStats = electricStats;
        }

        protected MetaValueItem setFluidStats(IFluidStats fluidStats) {
            if(fluidStats == null) {
                throw new IllegalArgumentException("Cannot set Fluid Stats to null.");
            }
            if(this.fluidStats != null) {
                throw new IllegalStateException("Tried to set Fluid Stats to " + fluidStats + ", but they're already set to " + this.fluidStats);
            }
            this.fluidStats = fluidStats;
            return this;
        }
        
        protected void setNuclearStats(INuclearStats nuclearStats) {
            if(nuclearStats == null) {
                throw new IllegalArgumentException("Cannot set Nuclear Stats to null.");
            }
            this.nuclearStats = nuclearStats;
            setMaxStackSize(1);
        }

        protected void setUseManager(IItemUseManager useManager) {
            if(this.useManager != null) {
                throw new IllegalStateException("Tried to set Use Manager to " + useManager + ", but it's already set to " + this.useManager);
            }
            this.useManager = useManager;
        }

        protected void addBehaviour(IItemBehaviour behaviour) {
            if (behaviour == null) {
                throw new IllegalArgumentException("Cannot add null behaviour.");
            }
            this.behaviours.add(behaviour);
        }

        public int getMetaValue() {
            return metaValue;
        }

        public IElectricStats getElectricStats() {
            return electricStats;
        }

        public IFluidStats getFluidStats() {
            return fluidStats;
        }

        public INuclearStats getNuclearStats() {
            return nuclearStats;
        }

        public IItemDurabilityManager getDurabilityManager() {
            return durabilityManager;
        }

        public List<IItemBehaviour> getBehaviours() {
            return ImmutableList.copyOf(behaviours);
        }

        public IItemUseManager getUseManager() {
            return useManager;
        }

        public int getBurnValue() {
            return burnValue;
        }

        public int getMaxStackSize() {
            return maxStackSize;
        }

        public boolean isVisible() {
            return visible;
        }

        public ItemStack getStackForm(int amount) {
            return new ItemStack(MetaItem.this, amount, metaItemOffset + metaValue);
        }

        public ItemStack getStackForm() {
            return getStackForm(1);
        }

    }

}