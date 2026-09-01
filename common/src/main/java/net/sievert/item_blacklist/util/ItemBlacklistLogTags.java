package net.sievert.item_blacklist.util;

public enum ItemBlacklistLogTags {

    CONFIG,
    INIT,
    ITEM,
    LOOT,
    RECIPE,
    TAG,
    TRADE;

    private final String id;

    ItemBlacklistLogTags() {
        this.id = name();
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }
}