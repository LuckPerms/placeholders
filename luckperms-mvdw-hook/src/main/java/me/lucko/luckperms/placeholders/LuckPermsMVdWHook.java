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

import me.lucko.luckperms.api.Contexts;
import me.lucko.luckperms.api.Group;
import me.lucko.luckperms.api.LuckPermsApi;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.api.Track;
import me.lucko.luckperms.api.User;
import me.lucko.luckperms.api.caching.PermissionData;
import me.lucko.luckperms.api.caching.UserData;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import be.maximvdw.placeholderapi.PlaceholderAPI;
import be.maximvdw.placeholderapi.PlaceholderReplaceEvent;
import be.maximvdw.placeholderapi.PlaceholderReplacer;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MVdWPlaceholderAPI Hook for LuckPerms, implemented using the LuckPerms API.
 */
public class LuckPermsMVdWHook extends JavaPlugin implements PlaceholderReplacer {

    private LuckPermsApi api;

    @Override
    public void onEnable() {
        if (!getServer().getServicesManager().isProvidedFor(LuckPermsApi.class)) {
            throw new RuntimeException("LuckPermsApi not provided.");
        }

        api = getServer().getServicesManager().load(LuckPermsApi.class);
        PlaceholderAPI.registerPlaceholder(this, "luckperms_*", this);
    }

    private Contexts makeContexts(User user) {
        Contexts contexts = api.getContextForUser(user).orElse(null);
        if (contexts == null) {
            contexts = Contexts.allowAll();
        }
        return contexts;
    }

    @Override
    public String onPlaceholderReplace(PlaceholderReplaceEvent event) {
        String placeholder = event.getPlaceholder();
        if (!placeholder.startsWith("luckperms_")) {
            return null;
        }

        String identifier = placeholder.substring("luckperms_".length()).toLowerCase();
        Player player = event.getPlayer();

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
            return user.getPrimaryGroup();
        }

        if (identifier.startsWith("context_") && identifier.length() > "context_".length()) {
            String key = identifier.substring("context_".length());
            return api.getContextForUser(user)
                    .map(Contexts::getContexts)
                    .map(c -> c.getValues(key))
                    .map(s -> Iterables.getFirst(s, ""))
                    .orElse("");
        }

        if (identifier.equals("groups")) {
            return user.getOwnNodes().stream().filter(Node::isGroupNode).map(Node::getGroupName).collect(Collectors.joining(", "));
        }

        if (identifier.startsWith("has_permission_") && identifier.length() > "has_permission_".length()) {
            String node = identifier.substring("has_permission_".length());
            return formatBoolean(user.hasPermission(api.buildNode(node).build()).asBoolean());
        }

        if (identifier.startsWith("inherits_permission_") && identifier.length() > "inherits_permission_".length()) {
            String node = identifier.substring("inherits_permission_".length());
            return formatBoolean(data.getPermissionData(makeContexts(user)).getPermissionValue(node).asBoolean());
        }

        if (identifier.startsWith("check_permission_") && identifier.length() > "check_permission_".length()) {
            String node = identifier.substring("check_permission_".length());
            return formatBoolean(data.getPermissionData(makeContexts(user)).getPermissionValue(node).asBoolean());
        }

        if (identifier.startsWith("in_group_") && identifier.length() > "in_group_".length()) {
            String groupName = identifier.substring("in_group_".length());
            return formatBoolean(user.getOwnNodes().stream().filter(Node::isGroupNode).map(Node::getGroupName).anyMatch(s -> s.equalsIgnoreCase(groupName)));
        }

        if (identifier.startsWith("inherits_group_") && identifier.length() > "inherits_group_".length()) {
            String groupName = identifier.substring("inherits_group_".length());
            return formatBoolean(data.getPermissionData(makeContexts(user)).getPermissionValue("group." + groupName).asBoolean());
        }

        if (identifier.startsWith("on_track_") && identifier.length() > "on_track_".length()) {
            String trackName = identifier.substring("on_track_".length());
            return api.getTrackSafe(trackName)
                    .map(t -> formatBoolean(t.containsGroup(user.getPrimaryGroup())))
                    .orElse("");
        }

        if (identifier.startsWith("has_groups_on_track_") && identifier.length() > "has_groups_on_track_".length()) {
            String trackName = identifier.substring("has_groups_on_track_".length());
            return api.getTrackSafe(trackName).map(t -> {
                boolean match = user.getOwnNodes().stream().filter(Node::isGroupNode).map(Node::getGroupName).anyMatch(t::containsGroup);
                return formatBoolean(match);
            }).orElse("");
        }

        if (identifier.equals("highest_group_by_weight")) {
            return user.getPermissions().stream()
                    .filter(Node::isGroupNode)
                    .map(Node::getGroupName)
                    .map(s -> api.getGroupSafe(s))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted((o1, o2) -> {
                        int ret = Integer.compare(o1.getWeight().orElse(0), o2.getWeight().orElse(0));
                        return ret == 1 ? 1 : -1;
                    })
                    .findFirst()
                    .map(Group::getName)
                    .orElse("");
        }

        if (identifier.equals("lowest_group_by_weight")) {
            return user.getPermissions().stream()
                    .filter(Node::isGroupNode)
                    .map(Node::getGroupName)
                    .map(s -> api.getGroupSafe(s))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted((o1, o2) -> {
                        int ret = Integer.compare(o1.getWeight().orElse(0), o2.getWeight().orElse(0));
                        return ret == 1 ? -1 : 1;
                    })
                    .findFirst()
                    .map(Group::getName)
                    .orElse("");
        }

        if (identifier.startsWith("first_group_on_tracks_") && identifier.length() > "first_group_on_tracks_".length()) {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(identifier.substring("first_group_on_tracks_".length()));
            PermissionData permData = data.getPermissionData(makeContexts(user));
            return tracks.stream()
                    .map(t -> api.getTrackSafe(t))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(Track::getGroups)
                    .map(groups -> groups.stream()
                            .filter(s -> permData.getPermissionValue("group." + s).asBoolean())
                            .findFirst()
                    )
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .orElse("");
        }

        if (identifier.startsWith("last_group_on_tracks_") && identifier.length() > "last_group_on_tracks_".length()) {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(identifier.substring("last_group_on_tracks_".length()));
            PermissionData permData = data.getPermissionData(makeContexts(user));
            return tracks.stream()
                    .map(t -> api.getTrackSafe(t))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(Track::getGroups)
                    .map(Lists::reverse)
                    .map(groups -> groups.stream()
                            .filter(s -> permData.getPermissionValue("group." + s).asBoolean())
                            .findFirst()
                    )
                    .filter(Optional::isPresent)
                    .map(Optional::get)
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
                    .map(e -> getTime((int) (e - currentTime)))
                    .orElse("");
        }

        if (identifier.startsWith("group_expiry_time_") && identifier.length() > "group_expiry_time_".length()) {
            String group = identifier.substring("group_expiry_time_".length());
            long currentTime = System.currentTimeMillis() / 1000L;
            return user.getPermissions().stream()
                    .filter(Node::isTemporary)
                    .filter(Node::isGroupNode)
                    .filter(n -> n.getGroupName().equals(group))
                    .map(Node::getExpiryUnixTime)
                    .findAny()
                    .map(e -> getTime((int) (e - currentTime)))
                    .orElse("");
        }

        if (identifier.equalsIgnoreCase("prefix")) {
            return Optional.ofNullable(data.calculateMeta(makeContexts(user)).getPrefix()).orElse("");
        }

        if (identifier.equalsIgnoreCase("suffix")) {
            return Optional.ofNullable(data.calculateMeta(makeContexts(user)).getSuffix()).orElse("");
        }

        if (identifier.startsWith("meta_") && identifier.length() > "meta_".length()) {
            String node = identifier.substring("meta_".length());
            return data.getMetaData(makeContexts(user)).getMeta().getOrDefault(node, "");
        }

        return null;
    }

    private static String formatBoolean(boolean b) {
        return b ? "yes" : "no";
    }

    private static String getTime(int seconds) {
        if (seconds == 0) {
            return "0s";
        }

        long minute = seconds / 60;
        seconds = seconds % 60;
        long hour = minute / 60;
        minute = minute % 60;
        long day = hour / 24;
        hour = hour % 24;

        StringBuilder time = new StringBuilder();
        if (day != 0) {
            time.append(day).append("d ");
        }
        if (hour != 0) {
            time.append(hour).append("h ");
        }
        if (minute != 0) {
            time.append(minute).append("m ");
        }
        if (seconds != 0) {
            time.append(seconds).append("s");
        }

        return time.toString().trim();
    }
}
