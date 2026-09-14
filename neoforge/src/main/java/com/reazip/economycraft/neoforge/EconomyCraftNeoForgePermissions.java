package com.reazip.economycraft.neoforge;

import com.reazip.economycraft.util.EconomyPermissions;
import com.reazip.economycraft.util.EconomyPermissions.Nodes;
import com.reazip.economycraft.util.PermissionCompat;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

public final class EconomyCraftNeoForgePermissions {
    private EconomyCraftNeoForgePermissions() {}

    private static final Map<String, PermissionNode<Boolean>> NODES = new HashMap<>();

    public static void bootstrap() {
        NeoForge.EVENT_BUS.addListener(PermissionGatherEvent.Nodes.class, EconomyCraftNeoForgePermissions::registerNodes);
        install();
    }

    public static void registerNodes(PermissionGatherEvent.Nodes event) {
        register(event, Nodes.ADMIN, (player, uuid, ctx) -> player != null && PermissionCompat.isAdmin(player));
        for (String node : Nodes.ADMIN_ALL) {
            if (node.equals(Nodes.ADMIN)) continue;
            register(event, node, (player, uuid, ctx) -> player != null && hasBlanketAdmin(player));
        }
        for (String node : Nodes.COMMAND_ALL) {
            register(event, node, (player, uuid, ctx) -> true);
        }
    }

    private static boolean hasBlanketAdmin(ServerPlayer player) {
        PermissionNode<Boolean> adminNode = NODES.get(Nodes.ADMIN);
        Boolean result = adminNode != null ? PermissionAPI.getPermission(player, adminNode) : null;
        return result != null ? result : PermissionCompat.isAdmin(player);
    }

    private static void register(PermissionGatherEvent.Nodes event, String fullNode,
                                  PermissionNode.PermissionResolver<Boolean> resolver) {
        int dot = fullNode.indexOf('.');
        String modId = fullNode.substring(0, dot);
        String path = fullNode.substring(dot + 1);
        PermissionNode<Boolean> node = new PermissionNode<>(modId, path, PermissionTypes.BOOLEAN, resolver);
        NODES.put(fullNode, node);
        event.addNodes(node);
    }

    public static void install() {
        EconomyPermissions.setBackend((source, node, fallback) -> {
            PermissionNode<Boolean> permissionNode = NODES.get(node);
            if (permissionNode == null) return fallback;
            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
            } catch (Exception e) {
                return fallback;
            }
            try {
                Boolean result = PermissionAPI.getPermission(player, permissionNode);
                return result != null ? result : fallback;
            } catch (Throwable t) {
                return fallback;
            }
        });
    }
}
