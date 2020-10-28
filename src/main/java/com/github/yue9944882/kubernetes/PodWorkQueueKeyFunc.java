package com.github.yue9944882.kubernetes;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.openapi.models.V1Pod;

import java.util.function.Function;

public class PodWorkQueueKeyFunc implements Function<KubernetesObject, Request> {
    public Request apply(KubernetesObject kubernetesObject) {
        // Extracts owner-reference from a pod, and maps it to a reconciler request.
        if (!(kubernetesObject instanceof V1Pod)) {
            throw new RuntimeException("unexpected input type:" + kubernetesObject.getClass());
        }
        V1Pod pod = (V1Pod) kubernetesObject;
        String rsName = Utils.getReplicaSetControllerOwnerRef(pod).getName();
        return new Request(pod.getMetadata().getNamespace(), rsName);
    }
}
