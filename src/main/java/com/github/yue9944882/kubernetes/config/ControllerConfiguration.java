package com.github.yue9944882.kubernetes.config;

import com.github.yue9944882.kubernetes.ReplicaSetReconciler;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.spring.extended.controller.annotation.GroupVersionResource;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformer;
import io.kubernetes.client.spring.extended.controller.annotation.KubernetesInformers;
import io.kubernetes.client.util.ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class ControllerConfiguration {

    @Bean
    public ApiClient kubernetesSharedClient() throws IOException {
        return ClientBuilder.defaultClient();
    }

    @Bean
    public SharedInformerFactory sharedInformerFactory() {
        return new ControllerSharedInformerFactory();
    }

    @Bean
    public ReplicaSetReconciler replicaSetReconciler(
            ApiClient apiClient,
            Lister<V1Pod> podLister,
            SharedInformer<V1Pod> podSharedInformer,
            Lister<V1ReplicaSet> rsLister,
            SharedInformer<V1ReplicaSet> rsSharedInformer) {
        return new ReplicaSetReconciler(apiClient, podLister, podSharedInformer, rsLister, rsSharedInformer);
    }

    @KubernetesInformers({
            @KubernetesInformer(
                    apiTypeClass = V1Pod.class,
                    apiListTypeClass = V1PodList.class,
                    groupVersionResource =
                    @GroupVersionResource(
                            apiGroup = "",
                            apiVersion = "v1",
                            resourcePlural = "pods")),
            @KubernetesInformer(
                    apiTypeClass = V1ReplicaSet.class,
                    apiListTypeClass = V1ReplicaSetList.class,
                    groupVersionResource =
                    @GroupVersionResource(
                            apiGroup = "apps",
                            apiVersion = "v1",
                            resourcePlural = "replicasets")),
    })
    class ControllerSharedInformerFactory extends SharedInformerFactory {
    }
}
