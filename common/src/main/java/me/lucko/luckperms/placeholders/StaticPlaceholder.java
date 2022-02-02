package me.lucko.luckperms.placeholders;

import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;

/**
 * A standard placeholder which don't accept any arguments
 */
@FunctionalInterface
interface StaticPlaceholder extends Placeholder {
    Object handle(Object player, User user, CachedDataManager userData, QueryOptions queryOptions);
}
