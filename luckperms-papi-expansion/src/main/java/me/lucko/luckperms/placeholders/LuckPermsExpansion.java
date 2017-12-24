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
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.util.TimeUtil;
import me.lucko.luckperms.api.Contexts;
import me.lucko.luckperms.api.Group;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.api.Track;
import me.lucko.luckperms.api.User;
import me.lucko.luckperms.api.caching.PermissionData;
import me.lucko.luckperms.api.caching.UserData;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PlaceholderAPI Expansion for LuckPerms, implemented using the LuckPerms API.
 */
public class LuckPermsExpansion extends PlaceholderExpansion {
    private static final String IDENTIFIER = "luckperms";
    private static final String PLUGIN_NAME = "LuckPerms";
    private static final String AUTHOR = "Luck";
    private static final String VERSION = "4.0";

    private LuckPermsApi api = null;

    @Override
    public boolean canRegister() {
        return Bukkit.getServicesManager().isProvidedFor(LuckPermsApi.class);
    }

    @Override
    public boolean register() {
        if (!canRegister()) {
            return false;
        }

        api = Bukkit.getServicesManager().getRegistration(LuckPermsApi.class).getProvider();
        return super.register();
    }

    private String formatGroupName(String groupName) {
        Group group = api.getGroup(groupName);
        if (group != null) {
            groupName = group.getFriendlyName();
        }
        return groupName;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null || api == null) {
            return "";
        }

        User user = api.getUserSafe(api.getUuidCache().getUUID(player.getUniqueId())).orElse(null);
        if (user == null) {
            return "";
        }

        UserData data = user.getCachedData();
        identifier = identifier.toLowerCase();

        if (identifier.equals("group_name")) {
            return formatGroupName(user.getPrimaryGroup());
        }

        if (identifier.startsWith("context_") && identifier.length() > "context_".length()) {
            String key = identifier.substring("context_".length());
            Set<String> values = api.getContextManager().getApplicableContext(player).getValues(key);
            if (values.isEmpty()) {
                return "";
            } else {
                return values.stream().collect(Collectors.joining(", "));
            }
        }

        if (identifier.equals("groups")) {
            return user.getOwnNodes().stream()
                    .filter(Node::isGroupNode)
                    .map(Node::getGroupName)
                    .map(this::formatGroupName)
                    .collect(Collectors.joining(", "));
        }

        if (identifier.startsWith("has_permission_") && identifier.length() > "has_permission_".length()) {
            String node = identifier.substring("has_permission_".length());
            return formatBoolean(user.getOwnNodes().stream().anyMatch(n -> n.getPermission().equals(node)));
        }

        if (identifier.startsWith("inherits_permission_") && identifier.length() > "inherits_permission_".length()) {
            String node = identifier.substring("inherits_permission_".length());
            return formatBoolean(user.getPermissions().stream().anyMatch(n -> n.getPermission().equals(node)));
        }

        if (identifier.startsWith("check_permission_") && identifier.length() > "check_permission_".length()) {
            String node = identifier.substring("check_permission_".length());
            return formatBoolean(player.hasPermission(node));
        }

        if (identifier.startsWith("in_group_") && identifier.length() > "in_group_".length()) {
            String groupName = identifier.substring("in_group_".length());
            return formatBoolean(user.getOwnNodes().stream().filter(Node::isGroupNode).map(Node::getGroupName).anyMatch(s -> s.equalsIgnoreCase(groupName)));
        }

        if (identifier.startsWith("inherits_group_") && identifier.length() > "inherits_group_".length()) {
            String groupName = identifier.substring("inherits_group_".length());
            return formatBoolean(player.hasPermission("group." + groupName));
        }

        if (identifier.startsWith("on_track_") && identifier.length() > "on_track_".length()) {
            String trackName = identifier.substring("on_track_".length());
            return formatBoolean(api.getTrackSafe(trackName)
                    .map(t -> t.containsGroup(user.getPrimaryGroup()))
                    .orElse(false));
        }

        if (identifier.startsWith("has_groups_on_track_") && identifier.length() > "has_groups_on_track_".length()) {
            String trackName = identifier.substring("has_groups_on_track_".length());
            return formatBoolean(api.getTrackSafe(trackName)
                    .map(t -> user.getOwnNodes().stream().filter(Node::isGroupNode).map(Node::getGroupName).anyMatch(t::containsGroup))
                    .orElse(false));
        }

        if (identifier.equals("highest_group_by_weight")) {
            return user.getPermissions().stream()
                    .filter(Node::isGroupNode)
                    .map(Node::getGroupName)
                    .map(s -> api.getGroup(s))
                    .filter(Objects::nonNull)
                    .sorted((o1, o2) -> {
                        int ret = Integer.compare(o1.getWeight().orElse(0), o2.getWeight().orElse(0));
                        return ret == 1 ? 1 : -1;
                    })
                    .findFirst()
                    .map(Group::getName)
                    .map(this::formatGroupName)
                    .orElse("");
        }

        if (identifier.equals("lowest_group_by_weight")) {
            return user.getPermissions().stream()
                    .filter(Node::isGroupNode)
                    .map(Node::getGroupName)
                    .map(s -> api.getGroup(s))
                    .filter(Objects::nonNull)
                    .sorted((o1, o2) -> {
                        int ret = Integer.compare(o1.getWeight().orElse(0), o2.getWeight().orElse(0));
                        return ret == 1 ? -1 : 1;
                    })
                    .findFirst()
                    .map(Group::getName)
                    .map(this::formatGroupName)
                    .orElse("");
        }

        if (identifier.startsWith("first_group_on_tracks_") && identifier.length() > "first_group_on_tracks_".length()) {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(identifier.substring("first_group_on_tracks_".length()));
            PermissionData permData = data.getPermissionData(api.getContextsForPlayer(player));
            return tracks.stream()
                    .map(t -> api.getTrack(t))
                    .filter(Objects::nonNull)
                    .map(Track::getGroups)
                    .map(groups -> groups.stream()
                            .filter(s -> permData.getPermissionValue("group." + s).asBoolean())
                            .findFirst()
                    )
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(this::formatGroupName)
                    .findFirst()
                    .orElse("");
        }

        if (identifier.startsWith("last_group_on_tracks_") && identifier.length() > "last_group_on_tracks_".length()) {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(identifier.substring("last_group_on_tracks_".length()));
            PermissionData permData = data.getPermissionData(api.getContextsForPlayer(player));
            return tracks.stream()
                    .map(t -> api.getTrack(t))
                    .filter(Objects::nonNull)
                    .map(Track::getGroups)
                    .map(Lists::reverse)
                    .map(groups -> groups.stream()
                            .filter(s -> permData.getPermissionValue("group." + s).asBoolean())
                            .findFirst()
                    )
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(this::formatGroupName)
                    .findFirst()
                    .orElse("");
        }

        if (identifier.startsWith("expiry_time_") && identifier.length() > "expiry_time_".length()) {
            String node = identifier.substring("expiry_time_".length());
            long currentTime = System.currentTimeMillis() / 1000L;
            return user.getPermissions().stream()
                    .filter(Node::isTemporary)
                    .filter(n -> n.getPermission().equals(node))
                    .map(Node::getExpiryUnixTime)
                    .findAny()
                    .map(e -> TimeUtil.getTime((int) (e - currentTime)))
                    .orElse("");
        }

        if (identifier.startsWith("group_expiry_time_") && identifier.length() > "group_expiry_time_".length()) {
            String group = identifier.substring("group_expiry_time_".length());
            long currentTime = System.currentTimeMillis() / 1000L;
            return user.getPermissions().stream()
                    .filter(Node::isTemporary)
                    .filter(Node::isGroupNode)
                    .filter(n -> n.getGroupName().equalsIgnoreCase(group))
                    .map(Node::getExpiryUnixTime)
                    .findAny()
                    .map(e -> TimeUtil.getTime((int) (e - currentTime)))
                    .orElse("");
        }

        if (identifier.equalsIgnoreCase("prefix")) {
            return Optional.ofNullable(data.calculateMeta(api.getContextsForPlayer(player)).getPrefix()).orElse("");
        }

        if (identifier.equalsIgnoreCase("suffix")) {
            return Optional.ofNullable(data.calculateMeta(api.getContextsForPlayer(player)).getSuffix()).orElse("");
        }

        if (identifier.startsWith("meta_") && identifier.length() > "meta_".length()) {
            String node = identifier.substring("meta_".length());
            return data.getMetaData(api.getContextsForPlayer(player)).getMeta().getOrDefault(node, "");
        }

        return null;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getPlugin() {
        return PLUGIN_NAME;
    }

    @Override
    public String getAuthor() {
        return AUTHOR;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    private static String formatBoolean(boolean b) {
        return b ? PlaceholderAPIPlugin.booleanTrue() : PlaceholderAPIPlugin.booleanFalse();
    }
}
