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

import be.maximvdw.placeholderapi.PlaceholderAPI;
import be.maximvdw.placeholderapi.PlaceholderReplaceEvent;
import be.maximvdw.placeholderapi.PlaceholderReplacer;
import net.luckperms.api.LuckPerms;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MVdWPlaceholderAPI Hook for LuckPerms, implemented using the LuckPerms API.
 */
public class LuckPermsMVdWHook extends JavaPlugin implements PlaceholderReplacer, PlaceholderPlatform {
    private LPPlaceholderProvider provider;

    @Override
    public void onEnable() {
        if (!getServer().getServicesManager().isProvidedFor(LuckPerms.class)) {
            throw new RuntimeException("LuckPerms API not provided.");
        }

        LuckPerms luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        this.provider = new LPPlaceholderProvider(this, luckPerms);
        PlaceholderAPI.registerPlaceholder(this, "luckperms_*", this);
    }

    @Override
    public String onPlaceholderReplace(PlaceholderReplaceEvent event) {
        String placeholder = event.getPlaceholder();
        if (!placeholder.startsWith("luckperms_")) {
            return null;
        }

        String identifier = placeholder.substring("luckperms_".length()).toLowerCase();
        Player player = event.getPlayer();

        if (player == null || this.provider == null) {
            return "";
        }

        return this.provider.onPlaceholderRequest(player, player.getUniqueId(), identifier);
    }

}
