package kh.edu.istad.ite.shared.enums;

/**
 * How one item's stock is shared out between the channels that sell it.
 *
 * There is one shelf either way. An item has one balance per option and every
 * sale comes off it — this does not split the stock into piles, it says how
 * much of the one pile each channel is allowed to reach.
 *
 * <p>{@code SHARED} — every channel may sell everything on hand. What the shop
 * had before allocation existed, and what it keeps until it says otherwise.
 *
 * <p>{@code ALLOCATED} — a channel may sell up to the number set against it,
 * and what is allocated to nobody is held back. The distinction only bites on
 * the last unit: under SHARED two channels are both shown it and one of them
 * loses the race, which is fine for a deep shelf and not fine for a shop
 * selling six cakes a day.
 */
public enum ChannelStockMode {
    SHARED,
    ALLOCATED
}
