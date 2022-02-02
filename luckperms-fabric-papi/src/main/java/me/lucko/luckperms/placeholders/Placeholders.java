package me.lucko.luckperms.placeholders;

import eu.pb4.placeholders.PlaceholderAPI;
import eu.pb4.placeholders.PlaceholderResult;
import eu.pb4.placeholders.TextParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;

public class Placeholders implements ModInitializer, PlaceholderPlatform {

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
                throw new RuntimeException("LuckPerms API not provided.");
            }

            registerPlaceholders();
        });
    }

    private void registerPlaceholders() {
        LuckPerms luckPerms = LuckPermsProvider.get();
        PlaceholderProvider provider = new LPPlaceholderProvider(this, luckPerms);
        Map<String, Placeholder> placeholders = provider.getPlaceholders();
        placeholders.forEach((s, placeholder) -> {
            // Trim the unneeded _ off the end of dynamic placeholders
            String trimmed = s.replaceAll("_$", "");
            PlaceholderAPI.register(new Identifier("luckperms", trimmed), ctx -> {

                if (ctx.hasPlayer()) {
                    ServerPlayerEntity player = ctx.getPlayer();
                    User user = luckPerms.getUserManager().getUser(player.getUuid());
                    if (user == null) return PlaceholderResult.invalid("No user!");

                    CachedDataManager data = user.getCachedData();
                    QueryOptions queryOptions = luckPerms.getContextManager().getQueryOptions(player);

                    Object result = null;
                    if (placeholder instanceof DynamicPlaceholder) {
                        DynamicPlaceholder dp = (DynamicPlaceholder) placeholder;
                        if (!ctx.hasArgument() && placeholders.containsKey(trimmed)) {
                            // Didn't use optional param
                            result = ((StaticPlaceholder) placeholders.get(trimmed)).handle(player, user, data, queryOptions);
                        } else {
                            result = dp.handle(player, user, data, queryOptions, ctx.getArgument());
                        }
                    } else if (placeholder instanceof StaticPlaceholder) {
                        StaticPlaceholder sp = (StaticPlaceholder) placeholder;
                        result = sp.handle(player, user, data, queryOptions);
                    }

                    if (result instanceof Boolean) {
                        result = this.formatBoolean((boolean) result);
                    }

                    return result == null ? PlaceholderResult.invalid() : PlaceholderResult.value(TextParser.parse(result.toString()));
                } else {
                    return PlaceholderResult.invalid("No player!");
                }
            });
        });
    }
}
