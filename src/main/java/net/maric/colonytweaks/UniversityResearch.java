package net.maric.colonytweaks;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.requestsystem.token.StandardToken;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private static final Map<String, Pending> PENDING = new HashMap<>();

    /**
     * A research that has been requested but not started yet, plus the colony item
     * requests standing in for its missing cost.
     *
     * <p>The tokens are what makes cancel possible. They are stored as identifier strings
     * because a {@code StandardToken} is just a UUID, and a pending research is matched
     * against a cancelled request by token rather than by cost item - so a request the
     * player made themselves for the same item is never mistaken for ours.
     */
    private static final class Pending
    {
        private final ResourceLocation branch;
        private final ResourceLocation researchId;
        private final Set<String> tokens = new LinkedHashSet<>();

        private Pending(final ResourceLocation branch, final ResourceLocation researchId)
        {
            this.branch = branch;
            this.researchId = researchId;
        }
    }

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
        final Pending pending = new Pending(research.getBranch(), research.getId());
        final boolean created = requestMissing(building, research, missing, requestedNames, pending.tokens);

        PENDING.put(key(colony, building), pending);

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
        final Pending pending = PENDING.get(key);
        if (pending == null)
        {
            return;
        }

        final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(pending.branch, pending.researchId);
        if (research == null)
        {
            PENDING.remove(key);
            return;
        }

        final ILocalResearchTree tree = colony.getResearchManager().getResearchTree();
        if (tree.getResearch(pending.branch, pending.researchId) != null)
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
        MaricColonyTweaks.LOGGER.info("MCTLOG autostart check {}: missing={} costCount={}",
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
            requestMissing(building, research, missing, null, pending.tokens);
        }
    }

    /**
     * Cancels a requested research, reached when its item delivery is cancelled by the player.
     *
     * <p>Without this the request the mod raised is re-created by the next
     * {@link #onUniversityTick} about 25 s later, because the tick re-arms any cost item that
     * is still missing, so cancelling a delivery appears to do nothing. Cancelling any one of
     * the pending research's own requests means "stop requesting this research", so the
     * whole pending entry goes: the marker first (so the re-entrant cancellations below are
     * no-ops), then the remaining delivery requests.
     */
    public static void onRequestCancelled(final IColony colony, final IToken<?> token)
    {
        final String cancelled = tokenId(token);
        if (cancelled == null)
        {
            return;
        }

        String matchedKey = null;
        Pending matched = null;
        for (final Map.Entry<String, Pending> entry : PENDING.entrySet())
        {
            if (entry.getValue().tokens.contains(cancelled))
            {
                matchedKey = entry.getKey();
                matched = entry.getValue();
                break;
            }
        }

        if (matched == null)
        {
            return;
        }

        // Marker first, so the cancellations below re-enter harmlessly and the university
        // tick cannot re-arm the research while we tear it down.
        PENDING.remove(matchedKey);

        for (final String other : matched.tokens)
        {
            if (other.equals(cancelled))
            {
                continue;
            }

            try
            {
                colony.getRequestManager().updateRequestState(new StandardToken(UUID.fromString(other)), RequestState.CANCELLED);
            }
            catch (final IllegalArgumentException e)
            {
                // Token no longer known to the manager, or no longer valid after a restart.
                MaricColonyTweaks.LOGGER.debug("MaricColonyTweaks: could not cancel leftover research request {}", other, e);
            }
        }

        final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(matched.branch, matched.researchId);
        final Component name = research == null
            ? Component.literal(matched.researchId.toString())
            : MutableComponent.create(research.getName());

        MessageUtils.format("mariccolonytweaks.research.cancelled", name)
            .sendTo(colony)
            .forAllPlayers();
        MaricColonyTweaks.LOGGER.info("[MaricColonyTweaks] pending research request cancelled: {}", matched.researchId);
    }

    private static String tokenId(final IToken<?> token)
    {
        if (token == null)
        {
            return null;
        }

        return String.valueOf(token.getIdentifier());
    }

    public static void writePending(final BuildingUniversity building, final CompoundTag tag)
    {
        final Pending pending = PENDING.get(persistKey(building));
        if (pending != null)
        {
            final CompoundTag data = new CompoundTag();
            data.putString("branch", pending.branch.toString());
            data.putString("research", pending.researchId.toString());
            final ListTag tokens = new ListTag();
            for (final String token : pending.tokens)
            {
                tokens.add(StringTag.valueOf(token));
            }

            data.put("tokens", tokens);
            tag.put("mariccolonytweaksPendingResearch", data);
        }
    }

    public static void readPending(final BuildingUniversity building, final CompoundTag tag)
    {
        if (tag.contains("mariccolonytweaksPendingResearch"))
        {
            final CompoundTag data = tag.getCompound("mariccolonytweaksPendingResearch");
            final Pending pending = new Pending(
                ResourceLocation.parse(data.getString("branch")),
                ResourceLocation.parse(data.getString("research")));

            final ListTag tokens = data.getList("tokens", Tag.TAG_STRING);
            for (int i = 0; i < tokens.size(); i++)
            {
                pending.tokens.add(tokens.getString(i));
            }

            PENDING.put(persistKey(building), pending);
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
    private static boolean requestMissing(final BuildingUniversity building, final IGlobalResearch research, final List<Integer> missing, final List<String> requestedNames, final Set<String> outTokens)
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
            if (outTokens != null)
            {
                final String id = tokenId(token);
                if (id != null)
                {
                    outTokens.add(id);
                }
            }

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
