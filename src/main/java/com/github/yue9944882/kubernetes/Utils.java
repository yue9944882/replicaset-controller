package com.github.yue9944882.kubernetes;

import io.kubernetes.client.openapi.models.*;

public class Utils {

    public static V1OwnerReference getReplicaSetControllerOwnerRef(V1Pod pod) {
        return pod.getMetadata().getOwnerReferences()
                .stream()
                .filter(ownerRef -> {
                    if (!ownerRef.getController()) {
                        return false;
                    }
                    if (!"apps/v1".equals(ownerRef.getApiVersion())) {
                        return false;
                    }
                    if (!"ReplicaSet".equals(ownerRef.getKind())) {
                        return false;
                    }
                    return true;
                })
                .findFirst()
                .orElse(null);
    }

    public static boolean isPodActive(V1Pod pod) {
        return !"Succeeded".equals(pod.getStatus().getPhase())
                && !"Failed".equals(pod.getStatus().getPhase())
                && pod.getMetadata().getDeletionTimestamp() == null;

    }

    public static boolean isPodReady(V1Pod pod) {
        V1PodCondition podCondition = getPodCondition(pod, "Ready");
        if (podCondition == null) {
            return false;
        }
        return "True".equals(podCondition.getStatus());
    }

    public static V1PodCondition getPodCondition(V1Pod pod, String type) {
        if (pod.getStatus().getConditions() == null) {
            return null;
        }
        return pod.getStatus().getConditions().stream()
                .filter(cond -> {
                    return type.equals(cond.getType());
                })
                .findFirst()
                .orElse(null);

    }

    public static V1ReplicaSetCondition getReplicaSetCondition(V1ReplicaSet rs, String type) {
        if (rs.getStatus().getConditions() == null) {
            return null;
        }
        return rs.getStatus().getConditions().stream()
                .filter(cond -> {
                    return type.equals(cond.getType());
                })
                .findFirst()
                .orElse(null);

    }
}
