/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

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

public class LuckPermsFabricPlaceholders implements ModInitializer, PlaceholderPlatform {

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
