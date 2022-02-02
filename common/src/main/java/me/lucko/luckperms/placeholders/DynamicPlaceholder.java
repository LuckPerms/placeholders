package me.lucko.luckperms.placeholders;

import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

/**
 * A placeholder which accepts an extra "argument" at the end of the placeholder
 */
@FunctionalInterface
interface DynamicPlaceholder extends Placeholder {
    Object handle(Object player, User user, CachedDataManager userData, QueryOptions queryOptions, String argument);
}
