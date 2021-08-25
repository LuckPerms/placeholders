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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.metastacking.DuplicateRemovalFunction;
import net.luckperms.api.metastacking.MetaStackDefinition;
import net.luckperms.api.metastacking.MetaStackElement;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.track.Track;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides LuckPerms placeholders using the {@link LuckPerms} API.
 */
public class LPPlaceholderProvider implements PlaceholderProvider {

    /**
     * The platform this provider is "providing" placeholders for.
     */
    private final PlaceholderPlatform platform;

    /**
     * The LuckPerms API instance
     */
    private final LuckPerms luckPerms;

    /**
     * The internal placeholders being "provided"
     */
    private final Map<String, Placeholder> placeholders;

    public LPPlaceholderProvider(PlaceholderPlatform platform, LuckPerms luckPerms) {
        this.platform = platform;
        this.luckPerms = luckPerms;
        
        // register placeholders
        PlaceholderBuilder builder = new PlaceholderBuilder();
        setup(builder);
        this.placeholders = builder.build();
    }

    private void setup(PlaceholderBuilder builder) {
        builder.addStatic("prefix", (player, user, userData, queryOptions) -> Strings.nullToEmpty(userData.getMetaData(queryOptions).getPrefix()));

        builder.addStatic("suffix", (player, user, userData, queryOptions) -> Strings.nullToEmpty(userData.getMetaData(queryOptions).getSuffix()));

        builder.addDynamic("meta", (player, user, userData, queryOptions, node) -> {
            String value = userData.getMetaData(queryOptions).getMetaValue(node);
            return value == null ? "" : value;
        });

        builder.addDynamic("meta_all", (player, user, userData, queryOptions, node) -> {
            List<String> values = userData.getMetaData(queryOptions).getMeta().getOrDefault(node, ImmutableList.of());
            return values.isEmpty() ? "" : String.join(", ", values);
        });

        builder.addDynamic("prefix_element", (player, user, userData, queryOptions, element) -> {
            MetaStackElement stackElement = this.luckPerms.getMetaStackFactory().fromString(element).orElse(null);
            if (stackElement == null) {
                return "ERROR: Invalid element!";
            }

            MetaStackDefinition stackDefinition = this.luckPerms.getMetaStackFactory().createDefinition(ImmutableList.of(stackElement), DuplicateRemovalFunction.RETAIN_ALL, "", "", "");
            QueryOptions newOptions = queryOptions.toBuilder()
                    .option(MetaStackDefinition.PREFIX_STACK_KEY, stackDefinition)
                    .option(MetaStackDefinition.SUFFIX_STACK_KEY, stackDefinition)
                    .build();

            return Strings.nullToEmpty(userData.getMetaData(newOptions).getPrefix());
        });

        builder.addDynamic("suffix_element", (player, user, userData, queryOptions, element) -> {
            MetaStackElement stackElement = this.luckPerms.getMetaStackFactory().fromString(element).orElse(null);
            if (stackElement == null) {
                return "ERROR: Invalid element!";
            }

            MetaStackDefinition stackDefinition = this.luckPerms.getMetaStackFactory().createDefinition(ImmutableList.of(stackElement), DuplicateRemovalFunction.RETAIN_ALL, "", "", "");
            QueryOptions newOptions = queryOptions.toBuilder()
                    .option(MetaStackDefinition.PREFIX_STACK_KEY, stackDefinition)
                    .option(MetaStackDefinition.SUFFIX_STACK_KEY, stackDefinition)
                    .build();

            return Strings.nullToEmpty(userData.getMetaData(newOptions).getSuffix());
        });

        builder.addStatic("context", (player, user, userData, queryOptions) ->
                this.luckPerms.getContextManager().getContext(player).toSet().stream()
                        .map(c -> c.getKey() + "=" + c.getValue())
                        .collect(Collectors.joining(", "))
        );
        builder.addDynamic("context", (player, user, userData, queryOptions, key) ->
                String.join(", ", this.luckPerms.getContextManager().getContext(player).getValues(key))
        );

        builder.addStatic("groups", (player, user, userData, queryOptions) ->
                user.getNodes(NodeType.INHERITANCE)
                        .stream()
                        .filter(n -> queryOptions.satisfies(n.getContexts()))
                        .map(InheritanceNode::getGroupName)
                        .map(this::convertGroupDisplayName)
                        .collect(Collectors.joining(", "))
        );

        builder.addStatic("inherited_groups", (player, user, userData, queryOptions) ->
                user.getInheritedGroups(queryOptions)
                        .stream()
                        .map(Group::getFriendlyName)
                        .collect(Collectors.joining(", "))
        );

        builder.addStatic("primary_group_name", (player, user, userData, queryOptions) -> convertGroupDisplayName(user.getPrimaryGroup()));

        builder.addDynamic("has_permission", (player, user, userData, queryOptions, node) ->
                user.getNodes().stream()
                        .filter(n -> queryOptions.satisfies(n.getContexts()))
                        .anyMatch(n -> n.getKey().equals(node))
        );

        builder.addDynamic("inherits_permission", (player, user, userData, queryOptions, node) ->
                user.resolveInheritedNodes(queryOptions).stream()
                        .filter(n -> n.getContexts().isSatisfiedBy(queryOptions.context()))
                        .anyMatch(n -> n.getKey().equals(node))
        );

        builder.addDynamic("check_permission", (player, user, userData, queryOptions, node) -> user.getCachedData().getPermissionData(queryOptions).checkPermission(node).asBoolean());

        builder.addDynamic("in_group", (player, user, userData, queryOptions, groupName) ->
                user.getNodes(NodeType.INHERITANCE).stream()
                        .filter(n -> queryOptions.satisfies(n.getContexts()))
                        .map(InheritanceNode::getGroupName)
                        .anyMatch(s -> s.equalsIgnoreCase(groupName))
        );

        builder.addDynamic("inherits_group", (player, user, userData, queryOptions, groupName) ->
                user.getInheritedGroups(queryOptions)
                        .stream()
                        .anyMatch(g -> g.getName().equalsIgnoreCase(groupName))
        );

        builder.addDynamic("on_track", (player, user, userData, queryOptions, trackName) ->
                Optional.ofNullable(this.luckPerms.getTrackManager().getTrack(trackName))
                        .map(t -> t.containsGroup(user.getPrimaryGroup()))
                        .orElse(false)
        );

        builder.addDynamic("has_groups_on_track", (player, user, userData, queryOptions, trackName) ->
                Optional.ofNullable(this.luckPerms.getTrackManager().getTrack(trackName))
                        .map(t -> user.getNodes(NodeType.INHERITANCE).stream()
                                .map(InheritanceNode::getGroupName)
                                .anyMatch(t::containsGroup)
                        )
                .orElse(false)
        );

        builder.addStatic("highest_group_by_weight", (player, user, userData, queryOptions) ->
                user.getNodes(NodeType.INHERITANCE).stream()
                        .filter(n -> queryOptions.satisfies(n.getContexts()))
                        .map(InheritanceNode::getGroupName)
                        .map(n -> this.luckPerms.getGroupManager().getGroup(n))
                        .filter(Objects::nonNull)
                        .max(Comparator.comparingInt(g -> g.getWeight().orElse(0)))
                        .map(Group::getName)
                        .map(this::convertGroupDisplayName)
                        .orElse("")
        );

        builder.addStatic("lowest_group_by_weight", (player, user, userData, queryOptions) ->
                user.getNodes(NodeType.INHERITANCE).stream()
                        .filter(n -> queryOptions.satisfies(n.getContexts()))
                        .map(InheritanceNode::getGroupName)
                        .map(n -> this.luckPerms.getGroupManager().getGroup(n))
                        .filter(Objects::nonNull)
                        .min(Comparator.comparingInt(g -> g.getWeight().orElse(0)))
                        .map(Group::getName)
                        .map(this::convertGroupDisplayName)
                        .orElse("")
        );

        builder.addStatic("highest_inherited_group_by_weight", (player, user, userData, queryOptions) ->
                user.getInheritedGroups(queryOptions).stream()
                        .max(Comparator.comparingInt(g -> g.getWeight().orElse(0)))
                        .map(Group::getName)
                        .map(this::convertGroupDisplayName)
                        .orElse("")
        );

        builder.addStatic("lowest_inherited_group_by_weight", (player, user, userData, queryOptions) ->
                user.getInheritedGroups(queryOptions).stream()
                        .min(Comparator.comparingInt(g -> g.getWeight().orElse(0)))
                        .map(Group::getName)
                        .map(this::convertGroupDisplayName)
                        .orElse("")
        );

        builder.addDynamic("current_group_on_track", (player, user, userData, queryOptions, trackName) -> {
            Track track = this.luckPerms.getTrackManager().getTrack(trackName);
            if (track == null) {
                return "";
            }

            List<Group> groups = user.getNodes(NodeType.INHERITANCE).stream()
                    .filter(n -> track.containsGroup(n.getGroupName()))
                    .filter(n -> queryOptions.satisfies(n.getContexts()))
                    .distinct()
                    .map(n -> this.luckPerms.getGroupManager().getGroup(n.getGroupName()))
                    .collect(Collectors.toList());

            if (groups.size() != 1) {
                return "";
            }

            return groups.get(0).getFriendlyName();
        });

        builder.addDynamic("next_group_on_track", (player, user, userData, queryOptions, trackName) -> {
            Track track = this.luckPerms.getTrackManager().getTrack(trackName);
            if (track == null || track.getGroups().size() <= 1) {
                return "";
            }

            List<Group> groups = user.getNodes(NodeType.INHERITANCE).stream()
                    .filter(n -> track.containsGroup(n.getGroupName()))
                    .filter(n -> queryOptions.satisfies(n.getContexts()))
                    .distinct()
                    .map(n -> this.luckPerms.getGroupManager().getGroup(n.getGroupName()))
                    .collect(Collectors.toList());

            if (groups.size() != 1) {
                return "";
            }

            return Strings.nullToEmpty(convertGroupDisplayName(track.getNext(groups.get(0))));
        });

        builder.addDynamic("previous_group_on_track", (player, user, userData, queryOptions, trackName) -> {
            Track track = this.luckPerms.getTrackManager().getTrack(trackName);
            if (track == null || track.getGroups().size() <= 1) {
                return "";
            }

            List<Group> groups = user.getNodes(NodeType.INHERITANCE).stream()
                    .filter(n -> track.containsGroup(n.getGroupName()))
                    .filter(n -> queryOptions.satisfies(n.getContexts()))
                    .distinct()
                    .map(n -> this.luckPerms.getGroupManager().getGroup(n.getGroupName()))
                    .collect(Collectors.toList());

            if (groups.size() != 1) {
                return "";
            }

            return Strings.nullToEmpty(convertGroupDisplayName(track.getPrevious(groups.get(0))));
        });

        builder.addDynamic("first_group_on_tracks", (player, user, userData, queryOptions, argument) -> {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(argument);
            Set<String> groups = user.getInheritedGroups(queryOptions).stream().map(Group::getName).collect(Collectors.toSet());

            return tracks.stream()
                    .map(n -> this.luckPerms.getTrackManager().getTrack(n))
                    .filter(Objects::nonNull)
                    .map(Track::getGroups)
                    .map(trackGroups -> trackGroups.stream().filter(groups::contains).findFirst())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .map(this::convertGroupDisplayName)
                    .orElse("");
        });

        builder.addDynamic("last_group_on_tracks", (player, user, userData, queryOptions, argument) -> {
            List<String> tracks = Splitter.on(',').trimResults().splitToList(argument);
            Set<String> groups = user.getInheritedGroups(queryOptions).stream().map(Group::getName).collect(Collectors.toSet());

            return tracks.stream()
                    .map(n -> this.luckPerms.getTrackManager().getTrack(n))
                    .filter(Objects::nonNull)
                    .map(Track::getGroups)
                    .map(Lists::reverse)
                    .map(trackGroups -> trackGroups.stream().filter(groups::contains).findFirst())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .map(this::convertGroupDisplayName)
                    .orElse("");
        });

        builder.addDynamic("expiry_time", (player, user, userData, queryOptions, node) ->
                user.getNodes().stream()
                        .filter(Node::hasExpiry)
                        .filter(n -> n.getKey().equals(node))
                        .filter(n -> queryOptions.satisfies(n.getContexts()))
                        .map(Node::getExpiryDuration)
                        .filter(Objects::nonNull)
                        .filter(d -> !d.isNegative())
                        .findFirst()
                        .map(this::formatDuration)
                        .orElse("")
        );

        builder.addDynamic("inherited_expiry_time", (player, user, userData, queryOptions, node) ->
                user.resolveInheritedNodes(queryOptions).stream()
                        .filter(Node::hasExpiry)
                        .filter(n -> n.getKey().equals(node))
                        .map(Node::getExpiryDuration)
                        .filter(Objects::nonNull)
                        .filter(d -> !d.isNegative())
                        .findFirst()
                        .map(this::formatDuration)
                        .orElse("")
        );

        builder.addDynamic("group_expiry_time", (player, user, userData, queryOptions, group) ->
                user.getNodes(NodeType.INHERITANCE).stream()
                        .filter(Node::hasExpiry)
                        .filter(n -> n.getGroupName().equals(group))
                        .filter(n -> queryOptions.satisfies(n.getContexts()))
                        .map(Node::getExpiryDuration)
                        .filter(Objects::nonNull)
                        .filter(d -> !d.isNegative())
                        .findFirst()
                        .map(this::formatDuration)
                        .orElse("")
        );

        builder.addDynamic("inherited_group_expiry_time", (player, user, userData, queryOptions, group) ->
                user.resolveInheritedNodes(queryOptions).stream()
                        .filter(Node::hasExpiry)
                        .filter(NodeType.INHERITANCE::matches)
                        .map(NodeType.INHERITANCE::cast)
                        .filter(n -> n.getGroupName().equals(group))
                        .map(Node::getExpiryDuration)
                        .filter(Objects::nonNull)
                        .filter(d -> !d.isNegative())
                        .findFirst()
                        .map(this::formatDuration)
                        .orElse("")
        );
    }

    @Override
    public String onPlaceholderRequest(Object player, UUID playerUuid, String placeholder) {
        User user = this.luckPerms.getUserManager().getUser(playerUuid);
        if (user == null) {
            return "";
        }

        CachedDataManager data = user.getCachedData();
        QueryOptions queryOptions = this.luckPerms.getContextManager().getQueryOptions(player);

        placeholder = placeholder.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, Placeholder> entry : this.placeholders.entrySet()) {
            String id = entry.getKey();

            Placeholder p = entry.getValue();
            boolean handled = false;
            Object result = null;

            if (p instanceof DynamicPlaceholder) {
                DynamicPlaceholder dp = (DynamicPlaceholder) p;

                if (placeholder.startsWith(id) && placeholder.length() > id.length()) {
                    String argument = placeholder.substring(id.length());
                    result = dp.handle(player, user, data, queryOptions, argument);
                    handled = true;
                }

            } else if (p instanceof StaticPlaceholder) {
                StaticPlaceholder sp = (StaticPlaceholder) p;

                if (placeholder.equals(id)) {
                    result = sp.handle(player, user, data, queryOptions);
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
     * Format a duration using the LuckPerms formatter.
     *
     * @param duration the duration
     * @return a formatted version of the duration
     */
    private String formatDuration(Duration duration) {
        return DurationFormatter.CONCISE.format(duration);
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
        if (groupName == null) {
            return null;
        }

        Group group = this.luckPerms.getGroupManager().getGroup(groupName);
        if (group != null) {
            groupName = group.getFriendlyName();
        }

        return groupName;
    }

    /**
     * Builds a placeholder map
     */
    private static final class PlaceholderBuilder {
        private final Map<String, Placeholder> placeholders = new LinkedHashMap<>();

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
        Object handle(Object player, User user, CachedDataManager userData, QueryOptions queryOptions, String argument);
    }

    /**
     * A standard placeholder which don't accept any arguments
     */
    @FunctionalInterface
    private interface StaticPlaceholder extends Placeholder {
        Object handle(Object player, User user, CachedDataManager userData, QueryOptions queryOptions);
    }

}
