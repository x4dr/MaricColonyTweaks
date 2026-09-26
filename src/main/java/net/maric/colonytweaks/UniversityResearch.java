package net.maric.colonytweaks;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * University research item requests.
 *
 * <p>Vanilla only checks the university chest and the clicking player's inventory for a
 * research cost ({@code GlobalResearch.hasEnoughResources}) and refuses to start otherwise.
 * With this feature, clicking a research whose items are elsewhere (typically the
 * Warehouse) makes the university place colony requests for the missing amounts; couriers
 * deliver them to the university chest, and once every cost item is present there the
 * research starts automatically.
 */
public final class UniversityResearch
{
    /**
     * Per-university pending research, keyed by "colonyId:x,y,z". Survives restarts via
     * the building NBT hooks in {@code BuildingUniversityResearchMixin}.
     */
    private static final Map<String, ResourceLocation[]> PENDING = new HashMap<>();

    private UniversityResearch()
    {
    }

    /**
     * Called from the TryResearchMessage mixin instead of vanilla's start path whenever
     * the cost is not fully available. Creates building-level Stack requests for every
     * missing cost item, remembers the research as pending, and informs the player.
     */
    public static void createMissingRequests(final ServerPlayer player, final IColony colony, final BuildingUniversity building, final IGlobalResearch research)
    {
        final List<Integer> missing = computeMissing(building, player, research);
        final List<String> requestedNames = new ArrayList<>();
        boolean created = requestMissing(building, research, missing, requestedNames);

        PENDING.put(key(colony, building), new ResourceLocation[] {research.getBranch(), research.getId()});

        if (created)
        {
            MessageUtils.format("mariccolonytweaks.research.requesting",
                    String.join(", ", requestedNames),
                    MutableComponent.create(research.getName()))
                .sendTo(player);
        }
        else
        {
            MessageUtils.format("mariccolonytweaks.research.pending",
                    MutableComponent.create(research.getName()))
                .sendTo(player);
        }
    }

    /**
     * Called from the BuildingUniversity mixin on every colony tick (10 s). If a research
     * is pending here: clear the state if it got started some other way; otherwise, once
     * all cost items sit in the university chest, consume them and start the research.
     */
    public static void onUniversityTick(final BuildingUniversity building, final IColony colony)
    {
        if (!MaricColonyTweaks.ENABLE_RESEARCH_REQUESTS.get())
        {
            return;
        }

        final String key = key(colony, building);
        final ResourceLocation[] pending = PENDING.get(key);
        if (pending == null)
        {
            return;
        }

        final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(pending[0], pending[1]);
        if (research == null)
        {
            PENDING.remove(key);
            return;
        }

        final ILocalResearchTree tree = colony.getResearchManager().getResearchTree();
        if (tree.getResearch(pending[0], pending[1]) != null)
        {
            // Started some other way (e.g. a later click with items present) - drop the marker.
            PENDING.remove(key);
            return;
        }

        // Only start when a researcher slot is free (vanilla cap = building level).
        final int inProgress = tree.getResearchInProgress().size();
        if (inProgress >= building.getBuildingLevel())
        {
            return;
        }

        final List<Integer> missing = computeMissing(building, null, research);
        MaricColonyTweaks.LOGGER.info("JBLOG autostart check {}: missing={} costCount={}",
            research.getId(), missing, research.getCostList().size());
        if (missing.stream().noneMatch(m -> m > 0))
        {
            consumeFromBuilding(building, research.getCostList());
            research.startResearch(tree);
            colony.getResearchManager().markDirty();
            MessageUtils.format("mariccolonytweaks.research.autostart", MutableComponent.create(research.getName()))
                .sendTo(colony)
                .forAllPlayers();
            PENDING.remove(key);
        }
        else
        {
            requestMissing(building, research, missing, null);
        }
    }

    public static void writePending(final BuildingUniversity building, final CompoundTag tag)
    {
        final ResourceLocation[] pending = PENDING.get(persistKey(building));
        if (pending != null)
        {
            final CompoundTag data = new CompoundTag();
            data.putString("branch", pending[0].toString());
            data.putString("research", pending[1].toString());
            tag.put("mariccolonytweaksPendingResearch", data);
        }
    }

    public static void readPending(final BuildingUniversity building, final CompoundTag tag)
    {
        if (tag.contains("mariccolonytweaksPendingResearch"))
        {
            final CompoundTag data = tag.getCompound("mariccolonytweaksPendingResearch");
            PENDING.put(persistKey(building), new ResourceLocation[] {
                ResourceLocation.parse(data.getString("branch")),
                ResourceLocation.parse(data.getString("research"))
            });
        }
    }

    private static String key(final IColony colony, final BuildingUniversity building)
    {
        return colony.getID() + ":" + building.getID().toShortString();
    }

    private static String persistKey(final BuildingUniversity building)
    {
        return building.getColony().getID() + ":" + building.getID().toShortString();
    }

    /**
     * Missing amount per entry of the research cost list, using the same matching rules
     * as vanilla (university chest first, then the clicking player; any item variant the
     * SizedIngredient accepts counts). {@code player} may be null for tick-time checks.
     */
    private static List<Integer> computeMissing(final BuildingUniversity building, final Player player, final IGlobalResearch research)
    {
        final List<Integer> missing = new ArrayList<>();
        for (final SizedIngredient cost : research.getCostList())
        {
            final int required = cost.count();
            int available = 0;
            if (required > 0)
            {
                available = InventoryUtils.hasBuildingEnoughElseCount(building,
                    stack -> IGlobalResearch.isUniversityResearchMatch(stack, cost), required);
                if (available < required && player != null)
                {
                    available += InventoryUtils.getItemCountInItemHandler(
                        new InvWrapper(player.getInventory()),
                        stack -> IGlobalResearch.isPlayerResearchMatch(stack, cost));
                }
            }
            missing.add(Math.max(0, required - available));
        }
        return missing;
    }

    /**
     * Creates one building-level request per missing item that has no open request yet.
     * Appends "count x display name" entries to {@code requestedNames} when non-null.
     */
    private static boolean requestMissing(final BuildingUniversity building, final IGlobalResearch research, final List<Integer> missing, final List<String> requestedNames)
    {
        final List<SizedIngredient> costList = research.getCostList();
        final Set<com.minecolonies.api.crafting.ItemStorage> openRequests = new HashSet<>();
        for (final IRequest<? extends Stack> request : building.getOpenRequestsOfType(-1, TypeToken.of(Stack.class)))
        {
            openRequests.add(new com.minecolonies.api.crafting.ItemStorage(request.getRequest().getStack()));
        }

        boolean created = false;
        for (int i = 0; i < costList.size(); i++)
        {
            final int missingCount = missing.get(i);
            if (missingCount <= 0)
            {
                continue;
            }
            final ItemStack[] candidates = costList.get(i).getItems();
            if (candidates == null || candidates.length == 0)
            {
                continue;
            }

            final ItemStack representative = candidates[0].copy();
            representative.setCount(missingCount);
            if (openRequests.contains(new com.minecolonies.api.crafting.ItemStorage(representative)))
            {
                continue;
            }

            // min = count: require the full missing amount in one delivery, like the
            // postbox "exact quantity" mode. min=1 lets the resolver complete the
            // request after delivering a single item and the rest never arrives.
            final var token = building.createRequest(new Stack(representative, missingCount, missingCount), false);
            MaricColonyTweaks.LOGGER.info("[MaricColonyTweaks] research request created: {} x{} for {}",
                representative.getHoverName().getString(), missingCount, research.getId());
            created = true;
            if (requestedNames != null)
            {
                requestedNames.add(missingCount + "x " + representative.getHoverName().getString());
            }
        }
        return created;
    }

    /**
     * Removes the full cost list from the university's own containers (the couriers
     * delivered everything there). Mirrors the building-side half of vanilla's
     * {@code reduceBuildingThenPlayerInventory}.
     */
    private static void consumeFromBuilding(final BuildingUniversity building, final List<SizedIngredient> costList)
    {
        final Level world = building.getColony().getWorld();
        for (final BlockPos pos : building.getContainers())
        {
            final BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity == null)
            {
                continue;
            }
            final IItemHandler handler = IItemHandlerCapProvider.wrap(blockEntity).getItemHandlerCap();
            if (handler == null)
            {
                continue;
            }

            for (final SizedIngredient cost : costList)
            {
                int toRemove = cost.count();
                for (int slot = 0; slot < handler.getSlots() && toRemove > 0; slot++)
                {
                    if (IGlobalResearch.isUniversityResearchMatch(handler.getStackInSlot(slot), cost))
                    {
                        toRemove -= handler.extractItem(slot, toRemove, false).getCount();
                    }
                }
            }
        }
    }
}
