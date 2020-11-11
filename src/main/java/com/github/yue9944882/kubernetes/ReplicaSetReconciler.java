package com.github.yue9944882.kubernetes;

import com.google.common.collect.Maps;
import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.spring.extended.controller.annotation.*;
import io.kubernetes.client.util.labels.EqualityMatcher;
import io.kubernetes.client.util.labels.LabelMatcher;
import io.kubernetes.client.util.labels.LabelSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

@KubernetesReconciler(
        watches =
        @KubernetesReconcilerWatches({
                @KubernetesReconcilerWatch(
                        apiTypeClass = V1Pod.class, // the reconciler needs to subscribe pod events.
                        workQueueKeyFunc = PodWorkQueueKeyFunc.class, // mapping pod event to reconciler-request
                        resyncPeriodMillis = 60 * 1000L // resync every 60s
                ),
                @KubernetesReconcilerWatch(
                        apiTypeClass = V1ReplicaSet.class,// the reconciler needs to subscribe replicaset events.
                        resyncPeriodMillis = 60 * 1000L // resync every 60s
                )
        }))
public class ReplicaSetReconciler implements Reconciler {

    private final static Logger logger = LoggerFactory.getLogger(ReplicaSetReconciler.class);

    @Autowired
    private Lister<V1Pod> podLister;

    @Autowired
    private SharedInformer<V1Pod> podInformer;

    @Autowired
    private Lister<V1ReplicaSet> rsLister;

    @Autowired
    private SharedInformer<V1ReplicaSet> rsInformer;

    @Autowired
    private ApiClient apiClient;


    // the filter only applies for ADD event from pod.
    // the method must be public-access.
    @AddWatchEventFilter(apiTypeClass = V1Pod.class)
    public boolean onAddFilter(V1Pod pod) {
        return Utils.getReplicaSetControllerOwnerRef(pod) != null;
    }

    // the filter only applies for UPDATE event from pod.
    // the method must be public-access.
    @UpdateWatchEventFilter(apiTypeClass = V1Pod.class)
    public boolean onUpdateFilter(V1Pod oldPod, V1Pod newPod) {
        return Utils.getReplicaSetControllerOwnerRef(newPod) != null;
    }

    // the filter only applies for DELETE event from pod.
    // the method must be public-access.
    @DeleteWatchEventFilter(apiTypeClass = V1Pod.class)
    public boolean onDeleteFilter(V1Pod pod, Boolean cacheStatusUnknown) {
        return Utils.getReplicaSetControllerOwnerRef(pod) != null;
    }

    // the reconciler won't be dealing with reconciler-requests until the ready-func passes.
    // the method must be public-access.
    @KubernetesReconcilerReadyFunc
    public boolean informerCacheReady() {
        return podInformer.hasSynced() && rsInformer.hasSynced();
    }

    // see the original go code at:
    // https://github.com/kubernetes/kubernetes/blob/master/pkg/controller/replicaset/replica_set.go
    public Result reconcile(Request request) {
        logger.info("starting to reconcile replicaset {}", request);

        CoreV1Api coreV1Api = new CoreV1Api(apiClient);
        AppsV1Api appsV1Api = new AppsV1Api(apiClient);
        V1ReplicaSet rs = this.rsLister.namespace(request.getNamespace()).get(request.getName());
        if (rs == null) {
            logger.info("replicaset {} already deleted", request);
            return new Result(false);
        }
        List<V1Pod> allPods = this.podLister.namespace(request.getNamespace()).list();
        List<V1Pod> filteredPods = filterActivePods(allPods);

        Executors.newSingleThreadExecutor().shutdown();
        LabelSelector labelSelector = LabelSelector.and(rs.getSpec().getSelector().getMatchLabels()
                .entrySet()
                .stream()
                .map(e -> EqualityMatcher.equal(e.getKey(), e.getValue()))
                .toArray(LabelMatcher[]::new));

        filteredPods = claimPods(rs, labelSelector, filteredPods);
        int currentReplicas = filteredPods.size();
        Throwable manageRsException = null;
        if (rs.getSpec().getReplicas() == currentReplicas) {
            logger.info("{}/{} already matches replicas expectation", request.getNamespace(), request.getName());

        } else if (rs.getSpec().getReplicas() > currentReplicas) {
            logger.info("{}/{} scaling up", request.getNamespace(), request.getName());
            try {
                coreV1Api.createNamespacedPod(rs.getMetadata().getNamespace(), getPodFromTemplate(rs), null, null, null);
            } catch (ApiException e) {
                logger.error("{}/{} failed scaling up: {}", request.getNamespace(), request.getName(), e.getResponseBody());
                manageRsException = e;
            }

        } else if (rs.getSpec().getReplicas() < currentReplicas) {
            logger.info("{}/{} scaling down", request.getNamespace(), request.getName());
            for (int i = 0; i < currentReplicas - rs.getSpec().getReplicas(); i++) {
                V1Pod deletingPod = filteredPods.get(i);
                try {
                    coreV1Api.deleteNamespacedPod(deletingPod.getMetadata().getName(), deletingPod.getMetadata().getNamespace(), null, null, null, null, null, null);
                } catch (ApiException e) {
                    logger.error("{}/{} failed scaling down: {}", request.getNamespace(), request.getName(), e.getResponseBody());
                    manageRsException = e;
                } catch (JsonSyntaxException e) {
                    // ignoring: https://github.com/kubernetes-client/java#known-issues
                }
            }
        } else {
            // this line should never reach
        }

        // calculating status
        logger.info("calculating status subresource for {}", request);
        V1ReplicaSetStatus newStatus = this.calculateStatus(rs, filteredPods, manageRsException);
        V1ReplicaSet copied = new V1ReplicaSet()
                .metadata(rs.getMetadata())
                .spec(rs.getSpec())
                .status(newStatus);
        try {
            appsV1Api.replaceNamespacedReplicaSetStatus(
                    rs.getMetadata().getName(),
                    rs.getMetadata().getNamespace(),
                    copied,
                    null,
                    null,
                    null);
        } catch (ApiException e) {
            logger.error("failed updating status subresource for replicaset {}/{}: {}", rs.getMetadata().getNamespace(), rs.getMetadata().getName(), e.getResponseBody());
            return new Result(true);
        }

        return new Result(false);
    }


    private static List<V1Pod> filterActivePods(List<V1Pod> allPods) {
        return allPods.stream()
                .filter(Utils::isPodActive)
                .collect(Collectors.toList());
    }


    private static List<V1Pod> claimPods(V1ReplicaSet rs, LabelSelector selector, List<V1Pod> pods) {
        return pods.stream()
                .filter(pod -> {
                    V1OwnerReference ref = Utils.getReplicaSetControllerOwnerRef(pod);
                    if (ref != null) {
                        // try release
                        if (!rs.getMetadata().getUid().equals(ref.getUid())) {
                            // if it doesn't belong to this replicaset
                            // ignore
                            return false;
                        }
                        if (selector.apply(pod.getMetadata().getLabels())) {
                            // if it matches the current labels
                            // it's already successfully claimed
                            // ignore
                            return true;
                        }
                        if (rs.getMetadata().getDeletionTimestamp() != null) {
                            // orphaning
                            // ignore
                            return false;
                        }
                        // do release
                        logger.info("replicaset {}/{} releasing pod {}", rs.getMetadata().getNamespace(), rs.getMetadata().getName(), pod.getMetadata().getName());
                    } else {
                        // try adopt
                        if (rs.getMetadata().getDeletionTimestamp() != null) {
                            // orphaning
                            // ignore
                            return false;
                        }
                        if (!selector.apply(pod.getMetadata().getLabels())) {
                            // if it doesn't matches the current labels
                            // ignore
                            return false;
                        }
                        if (pod.getMetadata().getDeletionTimestamp() != null) {
                            // Ignore if the pod is being deleted
                            return false;
                        }
                        // do adopt
                        logger.info("replicaset {}/{} adopting pod {}", rs.getMetadata().getNamespace(), rs.getMetadata().getName(), pod.getMetadata().getName());
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    private static V1Pod getPodFromTemplate(V1ReplicaSet rs) {
        return new V1Pod().metadata(
                new V1ObjectMeta()
                        .namespace(rs.getMetadata().getNamespace())
                        .generateName(rs.getMetadata().getName() + "-")
                        .labels(rs.getSpec().getTemplate().getMetadata().getLabels())
                        .annotations(rs.getSpec().getTemplate().getMetadata().getAnnotations())
                        .ownerReferences(Collections.singletonList(new V1OwnerReference()
                                .apiVersion(rs.getApiVersion())
                                .kind(rs.getKind())
                                .name(rs.getMetadata().getName())
                                .uid(rs.getMetadata().getUid())
                                .controller(true)
                                .blockOwnerDeletion(true)
                        )))
                .spec(rs.getSpec().getTemplate().getSpec());
    }

    private V1ReplicaSetStatus calculateStatus(V1ReplicaSet rs, List<V1Pod> filterPods, Throwable manageReplicasException) {
        V1ReplicaSetStatus newStatus = new V1ReplicaSetStatus()
                .replicas(rs.getStatus().getReplicas())
                .readyReplicas(rs.getStatus().getReadyReplicas())
                .availableReplicas(rs.getStatus().getAvailableReplicas())
                .fullyLabeledReplicas(rs.getStatus().getFullyLabeledReplicas())
                .conditions(rs.getStatus().getConditions())
                .observedGeneration(rs.getStatus().getObservedGeneration());

        // Count the number of pods that have labels matching the labels of the pod
        // template of the replica set, the matching pods may have more
        // labels than are in the template. Because the label of podTemplateSpec is
        // a superset of the selector of the replica set, so the possible
        // matching pods must be part of the filteredPods.
        int fullyLabeledReplicasCount = 0;
        int readyReplicasCount = 0;
        int availableReplicasCount = 0;
        Map<String, String> templateLabels = rs.getSpec().getTemplate().getMetadata().getLabels();
        for (V1Pod pod : filterPods) {
            if (Maps.difference(pod.getMetadata().getLabels(), templateLabels).areEqual()) {
                fullyLabeledReplicasCount++;
            }
            if (Utils.isPodReady(pod)) {
                readyReplicasCount++;
            }
        }

        V1ReplicaSetCondition replicaFailureCond = Utils.getReplicaSetCondition(rs, "ReplicaFailure");
        if (manageReplicasException != null && replicaFailureCond == null) {
            String reason = "";
            if (rs.getSpec().getReplicas() > filterPods.size()) {
                reason = "FailedCreate";
            }
            if (rs.getSpec().getReplicas() < filterPods.size()) {
                reason = "FailedDelete";
            }
            newStatus.getConditions().add(new V1ReplicaSetCondition()
                    .type("ReplicaFailure")
                    .status("False")
                    .reason(reason));
        } else if (manageReplicasException == null && replicaFailureCond != null) {
            newStatus.conditions(newStatus.getConditions().stream()
                    .filter(cond -> !"ReplicaFailure".equals(cond.getType()))
                    .collect(Collectors.toList()));
        }
        newStatus.replicas(filterPods.size())
                .fullyLabeledReplicas(fullyLabeledReplicasCount)
                .readyReplicas(readyReplicasCount)
                .availableReplicas(readyReplicasCount); //TODO: calculate available replicas
        return newStatus;
    }
}
