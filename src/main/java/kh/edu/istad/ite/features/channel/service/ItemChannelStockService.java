package kh.edu.istad.ite.features.channel.service;

import kh.edu.istad.ite.features.catalog.entity.Item;
import kh.edu.istad.ite.features.catalog.entity.ItemVariant;
import kh.edu.istad.ite.features.channel.dto.ChannelStockAvailabilityResponse;
import kh.edu.istad.ite.features.channel.dto.ItemChannelStockResponse;
import kh.edu.istad.ite.features.channel.dto.SaveItemChannelStockRequest;
import kh.edu.istad.ite.shared.enums.OrderChannel;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * How much of an item's one shelf each channel may sell.
 *
 * The shelf itself stays where it was — this decides nothing about what is on
 * hand, only about who is allowed to reach it.
 */
public interface ItemChannelStockService {

    ItemChannelStockResponse findSplit(UUID businessId, UUID itemId);

    ItemChannelStockResponse saveSplit(UUID businessId, UUID itemId, SaveItemChannelStockRequest request);

    /**
     * What one channel may still sell of one option.
     *
     * Two ceilings, and the lower wins: a channel cannot sell past its
     * allocation, and nobody can sell what is not on the shelf. Under
     * {@code SHARED} there is no allocation to cap it, so {@code onHand} comes
     * straight back — which is exactly how every channel behaved before this
     * existed.
     */
    BigDecimal availableFor(Item item, ItemVariant variant, OrderChannel channel, BigDecimal onHand);

    /**
     * Every ceiling one channel is under, in one read.
     *
     * For the till and the storefront, which show a stock figure beside every
     * item on the screen and cannot ask about them one at a time. Items the
     * shop has not split are absent: they have no ceiling, so what is on the
     * shelf is the answer.
     */
    List<ChannelStockAvailabilityResponse> findChannelAvailability(UUID businessId, String channelCode);

    /**
     * Refuses a line that would sell past what this channel was allowed.
     *
     * Silent for an item on {@code SHARED} — there is nothing to be past — so
     * this only ever stops a sale the shop itself said should not happen. It
     * checks the allocation alone and not the shelf: whether there is stock at
     * all is a separate question, answered where it always was.
     */
    void requireAllocation(Item item, ItemVariant variant, OrderChannel channel, BigDecimal baseQuantity);

    /**
     * Books a sale against the channel's allocation.
     *
     * Called where the sale settles, beside the movement that takes the stock
     * off the shelf. Does nothing for an item on {@code SHARED} — there is no
     * allocation to consume — so every checkout can call it unconditionally.
     */
    void consume(Item item, ItemVariant variant, OrderChannel channel, BigDecimal baseQuantity);
}
