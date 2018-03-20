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

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import me.lucko.luckperms.api.Group;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.api.Track;
import me.lucko.luckperms.api.User;
import me.lucko.luckperms.api.caching.PermissionData;
import me.lucko.luckperms.api.caching.UserData;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provides LuckPerms placeholders using the {@link LuckPermsApi}.
 */
public class LPPlaceholderProvider implements PlaceholderProvider {

    /**
     * The platform this provider is "providing" placeholders for.
     */
    private final PlaceholderPlatform platform;

    /**
     * The LuckPerms API instance
     */
    private final LuckPermsApi api;

    /**
     * The internal placeholders being "provided"
     */
    private final Map<String, Placeholder> placeholders;

    public LPPlaceholderProvider(PlaceholderPlatform platform, LuckPermsApi api) {
        this.platform = platform;
        this.api = api;
        
        // register placeholders
        PlaceholderBuilder builder = new PlaceholderBuilder();
        setup(builder);
        this.placeholders = builder.build();
    }

    private void setup(PlaceholderBuilder builder) {
        builder.addStatic("group_name", (player, user, userData) -> convertGroupDisplayName(user.getPrimaryGroup()));
        builder.addDynamic("context", (player, user, userData, key) ->
                this.api.getContextManager().getApplicableContext(player).getValues(key).stream()
                        .collect(Collectors.joining(", "))
        );
        builder.addStatic("groups", (player, user, userData) ->
                user.getOwnNodes()
                        .stream()
                        .filter(Node::isGroupNode)
                        .map(Node::getGroupName)
                        .map(this::convertGroupDisplayName)
                        .collect(Collectors.joining(", "))
        );
        builder.addDynamic("has_permission", (player, user, userData, node) ->
                user.getOwnNodes().stream()
                        .anyMatch(n -> n.getPermission().equals(node))
        );
        builder.addDynamic("inherits_permission", (player, user, userData, node) ->
                user.getAllNodes().stream()
                        .anyMatch(n -> n.getPermission().equals(node))
        );
        builder.addDynamic("check_permission", (player, user, userData, node) -> player.hasPermission(node));
        builder.addDynamic("in_group", (player, user, userData, groupName) ->
                user.getOwnNodes().stream()
                        .filter(Node::isGroupNode)
                        .map(Node::getGroupName)
                        .anyMatch(s -> s.equalsIgnoreCase(groupName))
        );
        builder.addDynamic("inherits_group", (player, user, userData, groupName) -> player.hasPermission("group." + groupName));
        builder.addDynamic("on_track", (player, user, userData, trackName) ->
                this.api.getTrackSafe(trackName)
                        .map(t -> t.containsGroup(user.getPrimaryGroup()))
                        .orElse(false)
        );
        builder.addDynamic("has_groups_on_track", (player, user, userData, trackName) ->
                this.api.getTrackSafe(trackName)
                        .map(t -> user.getOwnNodes().stream()
                                .filter(Node::isGroupNode)
                                .map(Node::getGroupName)
                                .anyMatch(t::containsGroup)
                        )
                .orElse(false)
        );
        builder.addStatic("highest_group_by_weight", (player, user, userData) ->
                user.getPermissions().stream()
                        .filter(Node::isGroupNode)
                        .map(Node::getGroupName)
                        .map(this.api::getGroup)
                        .filter(Objects::nonNull)
                        .sorted((o1, o2) -> {
                            int ret = Integer.compare(o1.getWeight().orElse(0), o2.getWeight().orElse(0));
                            return ret == 1 ? 1 : -1;
                        })
                        .findFirst()
                        .map(Group::getName)
                        .map(this::convertGroupDisplayName)
                        .orElse("")
        );
        builder.addStatic("lowest_group_by_weight", (player, user, userData) ->
                user.getPermissions().stream()
                        .filter(Node::isGroupNode)
                        .map(Node::getGroupName)
                        .map(this.api::getGroup)
                        .filter(Objects::nonNull)
                        .sorted((o1, o2) -> {
                            int ret = Integer.compare(o1.getWeight().orElse(0), o2.getWeight().orElse(0));
                            return ret == 1 ? -1 : 1;
                        })
                        .findFirst()
                        .map(Group::getName)
                        .map(this::convertGroupDisplayName)
                        .orElse("")
        );
        builder.addDynamic("first_group_on_tracks", (player, user, userData, argument) -> {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(argument);
            PermissionData permData = userData.getPermissionData(this.api.getContextsForPlayer(player));
            return tracks.stream()
                    .map(this.api::getTrack)
                    .filter(Objects::nonNull)
                    .map(Track::getGroups)
                    .map(groups -> groups.stream()
                            .filter(s -> permData.getPermissionValue("group." + s).asBoolean())
                            .findFirst()
                    )
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .map(this::convertGroupDisplayName)
                    .orElse("");
        });
        builder.addDynamic("last_group_on_tracks", (player, user, userData, argument) -> {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(argument);
            PermissionData permData = userData.getPermissionData(this.api.getContextsForPlayer(player));
            return tracks.stream()
                    .map(this.api::getTrack)
                    .filter(Objects::nonNull)
                    .map(Track::getGroups)
                    .map(Lists::reverse)
                    .map(groups -> groups.stream()
                            .filter(s -> permData.getPermissionValue("group." + s).asBoolean())
                            .findFirst()
                    )
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .map(this::convertGroupDisplayName)
                    .orElse("");
        });
        builder.addDynamic("expiry_time", (player, user, userData, node) -> {
            long currentTime = System.currentTimeMillis() / 1000L;
            return user.getPermissions().stream()
                    .filter(Node::isTemporary)
                    .filter(n -> n.getPermission().equals(node))
                    .map(Node::getExpiryUnixTime)
                    .findAny()
                    .map(e -> formatTime((int) (e - currentTime)))
                    .orElse("");
        });
        builder.addDynamic("group_expiry_time", (player, user, userData, group) -> {
            long currentTime = System.currentTimeMillis() / 1000L;
            return user.getPermissions().stream()
                    .filter(Node::isTemporary)
                    .filter(Node::isGroupNode)
                    .filter(n -> n.getGroupName().equalsIgnoreCase(group))
                    .map(Node::getExpiryUnixTime)
                    .findAny()
                    .map(e -> formatTime((int) (e - currentTime)))
                    .orElse("");
        });
        builder.addStatic("prefix", (player, user, userData) -> Strings.nullToEmpty(userData.calculateMeta(this.api.getContextsForPlayer(player)).getPrefix()));
        builder.addStatic("suffix", (player, user, userData) -> Strings.nullToEmpty(userData.calculateMeta(this.api.getContextsForPlayer(player)).getSuffix()));
        builder.addDynamic("meta", (player, user, userData, node) -> userData.getMetaData(this.api.getContextsForPlayer(player)).getMeta().getOrDefault(node, ""));
    }

    @Override
    public String onPlaceholderRequest(Player player, String placeholder) {
        User user = this.api.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return "";
        }

        UserData data = user.getCachedData();
        placeholder = placeholder.toLowerCase();

        for (Map.Entry<String, Placeholder> entry : this.placeholders.entrySet()) {
            String id = entry.getKey();

            Placeholder p = entry.getValue();
            boolean handled = false;
            Object result = null;

            if (p instanceof DynamicPlaceholder) {
                DynamicPlaceholder dp = (DynamicPlaceholder) p;

                if (placeholder.startsWith(id) && placeholder.length() > id.length()) {
                    String argument = placeholder.substring(id.length());
                    result = dp.handle(player, user, data, argument);
                    handled = true;
                }

            } else if (p instanceof StaticPlaceholder) {
                StaticPlaceholder sp = (StaticPlaceholder) p;

                if (placeholder.equals(id)) {
                    result = sp.handle(player, user, data);
                    handled = true;
                }
            }

            if (!handled) {
                continue;
            }

            if (result instanceof Boolean) {
                result = formatBoolean((boolean) result);
            }

            return result == null ? null : result.toString();
        }

        return null;
    }

    /**
     * Format a unix timestamp according to the placeholder platforms rules.
     *
     * @param time the time
     * @return a formatted version of the time
     */
    private String formatTime(int time) {
        return this.platform.formatTime(time);
    }

    /**
     * Format a boolean according to the placeholder platforms rules.
     *
     * @param value the boolean value
     * @return a formatted representation of the boolean
     */
    private String formatBoolean(boolean value) {
        return this.platform.formatBoolean(value);
    }

    /**
     * Returns the display name alias of the group, if it has one.
     *
     * @param groupName the group's "id"
     * @return a "display name" for the given group
     */
    private String convertGroupDisplayName(String groupName) {
        Group group = this.api.getGroup(groupName);
        if (group != null) {
            groupName = group.getFriendlyName();
        }
        return groupName;
    }

    /**
     * Builds a placeholder map
     */
    private static final class PlaceholderBuilder {
        private final Map<String, Placeholder> placeholders = new HashMap<>();

        public void addDynamic(String id, DynamicPlaceholder placeholder) {
            this.placeholders.put(id + "_", placeholder);
        }

        public void addStatic(String id, StaticPlaceholder placeholder) {
            this.placeholders.put(id, placeholder);
        }
        
        public Map<String, Placeholder> build() {
            return ImmutableMap.copyOf(this.placeholders);
        }
    }

    /**
     * Generic placeholder super interface
     */
    private interface Placeholder {

    }

    /**
     * A placeholder which accepts an extra "argument" at the end of the placeholder
     */
    @FunctionalInterface
    private interface DynamicPlaceholder extends Placeholder {
        Object handle(Player player, User user, UserData userData, String argument);
    }

    /**
     * A standard placeholder which don't accept any arguments
     */
    @FunctionalInterface
    private interface StaticPlaceholder extends Placeholder {
        Object handle(Player player, User user, UserData userData);
    }

}
